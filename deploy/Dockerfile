# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /src
COPY pom.xml ./
COPY contracts/pom.xml contracts/pom.xml
COPY backend/pom.xml backend/pom.xml
COPY desktop/pom.xml desktop/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl backend -am dependency:go-offline
COPY contracts/src contracts/src
COPY backend/src backend/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl backend -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates  && rm -rf /var/lib/apt/lists/* && groupadd --gid 10001 staffdesk  && useradd --uid 10001 --gid 10001 --no-create-home staffdesk
WORKDIR /app
COPY --from=build --chown=10001:10001 /src/backend/target/backend-1.0.0.jar /app/app.jar
COPY --chown=10001:10001 deploy/entrypoint.sh /app/entrypoint.sh
USER 10001:10001
ENV TZ=UTC JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD curl --fail --silent "http://localhost:${PORT:-8080}/actuator/health/readiness" >/dev/null || exit 1
ENTRYPOINT ["sh","/app/entrypoint.sh"]
