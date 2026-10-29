# =============================================================================
# Sprint cierre-D / I1 — Backend Dockerfile
# =============================================================================
# Multi-stage build:
#   1. build  — Maven + JDK 21, compiles + packages the jar
#   2. runtime — JRE 21, runs the fat jar
#
# Build:  docker build -t edushift-back:local -f edushift-back/Dockerfile edushift-back
# Run:    docker run --rm -p 8080:8080 edushift-back:local
# =============================================================================

# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk-jammy@sha256:742daacb9316be4b6fc60258f6e4ac2a11882f6c597373c92290a57bfad0d2e0 AS build
WORKDIR /workspace

# Resolve dependencies during the actual package lifecycle. Running
# dependency:go-offline first duplicates Maven resolution and may stall on
# plugins not needed by the package lifecycle.
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
# CACHE_BUST: bump or pass --build-arg CACHE_BUST=$(date +%s) / Render "Clear build cache"
# so source changes are never skipped by a stale layer cache.
ARG CACHE_BUST=2026-08-25-paas-oom
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && echo "cache-bust=${CACHE_BUST}" && ./mvnw -B -ntp -Dmaven.test.skip=true package

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-jammy@sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0 AS runtime

# OCR fallback for scanned pages in RAG PDFs. Spanish and English language
# data are installed explicitly so runtime behavior does not depend on PATH
# or packages from the host machine.
RUN apt-get update \
 && apt-get install -y --no-install-recommends wget util-linux tesseract-ocr tesseract-ocr-spa tesseract-ocr-eng \
 && rm -rf /var/lib/apt/lists/*

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

# Defaults for ~1GiB+ containers (Render Starter). Free 512Mi is too small for
# this monolith — expect OOM. Override JAVA_TOOL_OPTIONS on the host.
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    LOG_PATH=/var/log/edushift \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=55 -XX:InitialRAMPercentage=10 -XX:MaxMetaspaceSize=160m -XX:MetaspaceSize=96m -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC"

# Entrypoint starts as root so it can chown mounted log volumes, then drops
# to uid 1001 before launching the JVM.
USER root
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD grep -Eq '^Uid:[[:space:]]+1001[[:space:]]' /proc/1/status && wget -qO- http://127.0.0.1:8080/api/actuator/health || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
