# syntax=docker/dockerfile:1.7

# =============================================================================
# EduShift backend - multi-stage Dockerfile
# Stage 1: build con Maven (cache de dependencias)
# Stage 2: extracción de capas (layertools)
# Stage 3: runtime mínimo (JRE 21 Alpine, usuario no-root)
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: build
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Cachear dependencias antes de copiar fuentes (mejor reuso de capas)
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp dependency:go-offline

COPY src ./src

ARG MAVEN_OPTS=""
ARG SKIP_TESTS=true
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp clean package -DskipTests=${SKIP_TESTS}

# -----------------------------------------------------------------------------
# Stage 2: extract (Spring Boot layertools - mejor cache en runtime)
#
# El jar fuente se aloja en /jar y la extracción va a /extracted (limpio).
# Spring Boot >=3.3 exige que `--destination` esté vacío o no exista; tener
# el jar en el mismo directorio destino dispara
# "destination already exists and is not empty".
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /jar
COPY --from=builder /workspace/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination /extracted

# -----------------------------------------------------------------------------
# Stage 3: runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache tzdata curl \
    && addgroup -S edushift \
    && adduser -S -G edushift -h /app edushift

WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    TZ=UTC \
    JAVA_OPTS="" \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Orden: capas más estables primero (mejor cache)
COPY --from=extractor --chown=edushift:edushift /extracted/app/dependencies/ ./
COPY --from=extractor --chown=edushift:edushift /extracted/app/spring-boot-loader/ ./
COPY --from=extractor --chown=edushift:edushift /extracted/app/snapshot-dependencies/ ./
COPY --from=extractor --chown=edushift:edushift /extracted/app/application/ ./

USER edushift

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://localhost:${SERVER_PORT}/api/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
