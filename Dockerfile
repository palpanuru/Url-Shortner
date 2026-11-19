# syntax=docker/dockerfile:1
# --- build stage -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml spotbugs-exclude.xml ./
RUN mvn -B -q -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -q -ntp package -DskipTests -Dspotbugs.skip=true -Djacoco.skip=true

# --- runtime stage: JRE only, non-root user ----------------------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /src/target/url-shortener-*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# No HEALTHCHECK: the JRE image ships no curl/wget. Orchestrators should probe
# /actuator/health/liveness and /actuator/health/readiness directly.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
