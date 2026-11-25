#!/usr/bin/env bash
# Provisions Paycore on Azure from infra/main.bicep, then points GitHub Actions at it so every push to main
# deploys. Safe to re-run: existing secrets are reused, so a re-run doesn't rotate passwords or log users out.
#
#   az login
#   ALERT_EMAIL=you@example.com scripts/azure-deploy.sh
#
# Optional: RESOURCE_GROUP, LOCATION, IMAGE, GITHUB_REPO, MIN_REPLICAS.
set -euo pipefail

RESOURCE_GROUP=${RESOURCE_GROUP:-paycore-rg}
LOCATION=${LOCATION:-centralindia}
GITHUB_REPO=${GITHUB_REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}
IMAGE=${IMAGE:-ghcr.io/${GITHUB_REPO%%/*}/paycore:latest}
MIN_REPLICAS=${MIN_REPLICAS:-0}
ALERT_EMAIL=${ALERT_EMAIL:?set ALERT_EMAIL to the address that should get alerts}
APP=paycore

cd "$(dirname "$0")/.."

echo "Subscription: $(az account show --query name -o tsv)"
for provider in Microsoft.App Microsoft.DBforPostgreSQL Microsoft.OperationalInsights Microsoft.Insights \
    Microsoft.ManagedIdentity; do
  az provider register --namespace "$provider" --wait -o none
done

az group create --name "$RESOURCE_GROUP" --location "$LOCATION" -o none

existing_secret() {
  az containerapp secret show -g "$RESOURCE_GROUP" -n "$APP" --secret-name "$1" --query value -o tsv 2>/dev/null || true
}
POSTGRES_PASSWORD=$(existing_secret db-password)
JWT_SECRET=$(existing_secret jwt-secret)
# Azure requires three of: upper, lower, digit, symbol. The suffix guarantees that whatever base64 produced.
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-"$(openssl rand -base64 30 | tr -d '/+=')Aa1-"}
JWT_SECRET=${JWT_SECRET:-$(openssl rand -base64 48)}

echo "Deploying infrastructure to $RESOURCE_GROUP ($LOCATION). PostgreSQL takes 5-10 minutes the first time..."
read -r APP_URL CLIENT_ID < <(az deployment group create \
  --resource-group "$RESOURCE_GROUP" \
  --name "paycore-$(date +%Y%m%d%H%M%S)" \
  --template-file infra/main.bicep \
  --parameters name="$APP" image="$IMAGE" githubRepo="$GITHUB_REPO" alertEmail="$ALERT_EMAIL" \
               minReplicas="$MIN_REPLICAS" postgresPassword="$POSTGRES_PASSWORD" jwtSecret="$JWT_SECRET" \
  --query "[properties.outputs.appUrl.value, properties.outputs.deployClientId.value]" -o tsv | paste -s -)

echo "Pointing GitHub Actions at the deployment..."
gh variable set AZURE_CLIENT_ID --repo "$GITHUB_REPO" --body "$CLIENT_ID"
gh variable set AZURE_TENANT_ID --repo "$GITHUB_REPO" --body "$(az account show --query tenantId -o tsv)"
gh variable set AZURE_SUBSCRIPTION_ID --repo "$GITHUB_REPO" --body "$(az account show --query id -o tsv)"
gh variable set AZURE_RESOURCE_GROUP --repo "$GITHUB_REPO" --body "$RESOURCE_GROUP"
gh variable set AZURE_CONTAINERAPP --repo "$GITHUB_REPO" --body "$APP"

echo "Waiting for $APP_URL/actuator/health (a cold start takes a minute or two)..."
for _ in $(seq 1 30); do
  if curl -fsS --max-time 10 "$APP_URL/actuator/health" | grep -q '"UP"'; then
    echo "Paycore is up: $APP_URL/swagger-ui.html"
    exit 0
  fi
  sleep 10
done
echo "Health check did not pass. Logs: az containerapp logs show -g $RESOURCE_GROUP -n $APP --container paycore" >&2
exit 1
