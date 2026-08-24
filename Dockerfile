# =============================================================================
# Sprint cierre-D / I1 — Backend Dockerfile
# =============================================================================
# Multi-stage build:
#   1. build  — Maven + JDK 21, compiles + packages the jar
#   2. runtime — JRE 21, runs the fat jar
#
# Build:  docker build -t edushift-back:local -f edushift-back/Dockerfile edushift-back
# Run:    docker run --rm -p 8081:8081 edushift-back:local
# =============================================================================

# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Cache dependencies separately from source for faster incremental builds.
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# Now copy source and build.
COPY src ./src
RUN ./mvnw -B -ntp -DskipTests package

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-jammy AS runtime

# Run as non-root (defence in depth; ECS/Fargate also has its own UID).
RUN groupadd --system --gid 1001 edushift \
 && useradd  --system --uid 1001 --gid edushift --home-dir /app --shell /sbin/nologin edushift

WORKDIR /app

# Spring Boot fat jar — single file contains all deps + classes.
COPY --from=build /workspace/target/*.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

# Logback (prod) writes rolling files under LOG_PATH. Create the dirs as root
# and hand them to the runtime user — otherwise start fails with
# FileNotFoundException on ./logs/*.log (non-root cannot mkdir under /app).
RUN mkdir -p /var/log/edushift/archive /app/logs/archive /app/uploads \
 && chown -R edushift:edushift /var/log/edushift /app/logs /app/uploads \
 && chmod +x /app/docker-entrypoint.sh

# Defaults overridable via -e ... at run time. See
# scripts/sprint-9b-launch.ps1 for the dev-profile env vars; for prod
# you'll set DB_HOST/DB_USER/DB_PASSWORD via secrets.
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8081 \
    LOG_PATH=/var/log/edushift \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

# Entrypoint starts as root so it can chown mounted log volumes, then drops
# to uid 1001 before launching the JVM.
USER root
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8081/api/actuator/health || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
