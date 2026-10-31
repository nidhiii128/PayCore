# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/paycore-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 paycore
WORKDIR /app
# Azure Monitor OpenTelemetry agent. Dormant unless JAVA_TOOL_OPTIONS attaches it, which only the Azure deployment does.
ARG APPINSIGHTS_AGENT_VERSION=3.7.10
ADD --checksum=sha256:93a70c8f5d364c7e777f6c4d1b235dba91aef8448bd3fa94359f1d7f3e2eb0ec --chmod=644 \
    https://repo1.maven.org/maven2/com/microsoft/azure/applicationinsights-agent/${APPINSIGHTS_AGENT_VERSION}/applicationinsights-agent-${APPINSIGHTS_AGENT_VERSION}.jar \
    applicationinsights-agent.jar
COPY --from=build /workspace/app.jar app.jar
USER paycore
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
