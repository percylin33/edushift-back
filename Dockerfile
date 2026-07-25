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

# Defaults overridable via -e ... at run time. See
# scripts/sprint-9b-launch.ps1 for the dev-profile env vars; for prod
# you'll set DB_HOST/DB_USER/DB_PASSWORD via secrets.
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8081 \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

USER edushift
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8081/api/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
