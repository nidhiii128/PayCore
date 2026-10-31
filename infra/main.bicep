// Paycore on Azure, sized to stay inside the free tiers of an Azure for Students subscription:
//   - Container Apps (consumption, scales to zero) running the app with Redis as a sidecar
//   - PostgreSQL Flexible Server on the free Burstable B1ms SKU
//   - Application Insights (OpenTelemetry Java agent) with an error-rate alert
//   - A user-assigned identity that GitHub Actions logs in as through OIDC, so no Azure secret lives in GitHub

targetScope = 'resourceGroup'

@description('Azure region for every resource.')
param location string = resourceGroup().location

@description('Prefix for resource names. Also the container app name.')
param name string = 'paycore'

@description('Image to run. GitHub Actions replaces it on every push to main.')
param image string

@description('Repository allowed to deploy, as owner/name.')
param githubRepo string

@description('Who gets the error-rate alert.')
param alertEmail string

@description('0 scales to zero when idle (free, but cold starts and no background jobs while idle). 1 keeps it warm.')
@minValue(0)
@maxValue(1)
param minReplicas int = 0

@secure()
param postgresPassword string

@secure()
param jwtSecret string

var suffix = uniqueString(resourceGroup().id)
var postgresAdmin = 'paycore'

// ---------- Observability ----------

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${name}-logs-${suffix}'
  location: location
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
    // 5 GB/month of ingestion is free; cap the day at a matching share so a log storm can't run up a bill.
    workspaceCapping: { dailyQuotaGb: json('0.15') }
  }
}

resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: '${name}-ai-${suffix}'
  location: location
  kind: 'web'
  properties: {
    Application_Type: 'web'
    WorkspaceResourceId: logs.id
  }
}

resource alertGroup 'Microsoft.Insights/actionGroups@2023-01-01' = {
  name: '${name}-alerts'
  location: 'global'
  properties: {
    groupShortName: name
    enabled: true
    emailReceivers: [
      { name: 'owner', emailAddress: alertEmail, useCommonAlertSchema: true }
    ]
  }
}

resource failedRequestsAlert 'Microsoft.Insights/metricAlerts@2018-03-01' = {
  name: '${name}-failed-requests'
  location: 'global'
  properties: {
    description: 'More than 5 failed requests in 5 minutes.'
    severity: 2
    enabled: true
    scopes: [ appInsights.id ]
    evaluationFrequency: 'PT5M'
    windowSize: 'PT5M'
    criteria: {
      'odata.type': 'Microsoft.Azure.Monitor.SingleResourceMultipleMetricCriteria'
      allOf: [
        {
          criterionType: 'StaticThresholdCriterion'
          name: 'failed-requests'
          metricNamespace: 'microsoft.insights/components'
          metricName: 'requests/failed'
          operator: 'GreaterThan'
          threshold: 5
          timeAggregation: 'Count'
        }
      ]
    }
    actions: [
      { actionGroupId: alertGroup.id }
    ]
  }
}

// ---------- Database ----------

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: '${name}-pg-${suffix}'
  location: location
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    version: '16'
    administratorLogin: postgresAdmin
    administratorLoginPassword: postgresPassword
    storage: { storageSizeGB: 32 }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: { mode: 'Disabled' }
    network: { publicNetworkAccess: 'Enabled' }
  }
}

// 0.0.0.0 is Azure's convention for "allow connections from Azure services", which covers Container Apps
// without a VNet (a VNet would need paid components).
resource allowAzure 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: postgres
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource database 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgres
  name: 'paycore'
}

// ---------- App ----------

resource environment 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${name}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

resource app 'Microsoft.App/containerApps@2024-03-01' = {
  name: name
  location: location
  properties: {
    managedEnvironmentId: environment.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
        allowInsecure: false
      }
      secrets: [
        { name: 'db-password', value: postgresPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'appinsights-connection-string', value: appInsights.properties.ConnectionString }
      ]
    }
    template: {
      containers: [
        {
          name: 'paycore'
          image: image
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            { name: 'DATABASE_URL', value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/${database.name}?sslmode=require' }
            { name: 'DATABASE_USERNAME', value: postgresAdmin }
            { name: 'DATABASE_PASSWORD', secretRef: 'db-password' }
            // B1ms allows about 50 connections, a few of which Azure reserves.
            { name: 'DATABASE_POOL_SIZE', value: '10' }
            { name: 'REDIS_HOST', value: 'localhost' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', secretRef: 'appinsights-connection-string' }
            { name: 'APPLICATIONINSIGHTS_ROLE_NAME', value: name }
            // The agent would otherwise try to write its log file into /app, which the non-root user can't.
            { name: 'APPLICATIONINSIGHTS_SELF_DIAGNOSTICS_DESTINATION', value: 'console' }
            { name: 'JAVA_TOOL_OPTIONS', value: '-javaagent:/app/applicationinsights-agent.jar' }
          ]
          probes: [
            {
              // Spring Boot plus Flyway needs a while on a cold start; allow up to ~3 minutes.
              type: 'Startup'
              httpGet: { path: '/actuator/health/liveness', port: 8080 }
              initialDelaySeconds: 10
              periodSeconds: 5
              failureThreshold: 36
            }
            {
              type: 'Liveness'
              httpGet: { path: '/actuator/health/liveness', port: 8080 }
              periodSeconds: 10
            }
            {
              type: 'Readiness'
              httpGet: { path: '/actuator/health/readiness', port: 8080 }
              periodSeconds: 10
            }
          ]
        }
        {
          // Redis only backs rate limiting, which fails open, so a per-replica sidecar is enough and avoids
          // Azure Cache for Redis, which has no free tier. Counters are per replica, hence maxReplicas: 1.
          name: 'redis'
          image: 'docker.io/library/redis:7-alpine'
          args: [ '--save', '', '--appendonly', 'no' ]
          resources: {
            cpu: json('0.25')
            memory: '0.5Gi'
          }
        }
      ]
      scale: {
        minReplicas: minReplicas
        maxReplicas: 1
      }
    }
  }
}

// ---------- GitHub Actions deploy identity ----------

resource deployIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: '${name}-github-deploy'
  location: location
}

resource githubFederation 'Microsoft.ManagedIdentity/userAssignedIdentities/federatedIdentityCredentials@2023-01-31' = {
  parent: deployIdentity
  name: 'github-main'
  properties: {
    issuer: 'https://token.actions.githubusercontent.com'
    subject: 'repo:${githubRepo}:ref:refs/heads/main'
    audiences: [ 'api://AzureADTokenExchange' ]
  }
}

// Contributor on this resource group only: enough to roll out a new revision, nothing outside it.
var contributorRoleId = 'b24988ac-6180-42a0-ab88-20f7382dd24c'

resource deployRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(resourceGroup().id, deployIdentity.id, contributorRoleId)
  properties: {
    principalId: deployIdentity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', contributorRoleId)
  }
}

output appUrl string = 'https://${app.properties.configuration.ingress.fqdn}'
output deployClientId string = deployIdentity.properties.clientId
output postgresHost string = postgres.properties.fullyQualifiedDomainName
