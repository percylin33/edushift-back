# syntax=docker/dockerfile:1.7

# =============================================================================
# EduShift backend - multi-stage Dockerfile
# Stage 1: build con Maven (cache de dependencias)
# Stage 2: runtime mínimo (JRE 21 Alpine, usuario no-root)
#
# Antes se usaba `extract --layers` (Spring Boot layertools) entre stages 2 y 3
# para separar dependencias / snapshot-dependencies / application en capas
# independientes. Eso requería que el manifest principal del jar quedara en la
# imagen runtime (clase `org.springframework.boot.loader.launch.JarLauncher`) y
# el comando COPY no incluía `META-INF/`. Resultado en runtime:
#   "Could not find or load main class org.springframework.boot.loader.launch.JarLauncher"
#   "java.lang.ClassNotFoundException: org.springframework.boot.loader.launch.JarLauncher"
# Spring Boot 3.3+ ya no genera una capa `META-INF` separada cuando usás
# `--layers`, por lo que el patrón es frágil. Se reemplaza por el método
# directo y portable: copiar el jar ejecutable y arrancarlo con `java -jar`.
# El cache de Maven lo da BuildKit con `--mount=type=cache,target=/root/.m2`.
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
# Git en Windows no conserva el bit +x; chmod explícito antes de ejecutar.
RUN chmod +x ./mvnw
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp dependency:go-offline

COPY src ./src

ARG MAVEN_OPTS=""
ARG SKIP_TESTS=true
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp clean package -DskipTests=${SKIP_TESTS}

# -----------------------------------------------------------------------------
# Stage 2: runtime
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

# Copiamos el jar ejecutable completo (Spring Boot fat-jar). Arrancamos con
# `java -jar` para que BOOT-INF/classes y BOOT-INF/lib viajen juntos.
COPY --from=builder --chown=edushift:edushift /workspace/target/edushift-back-*.jar /app/app.jar

USER edushift

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://localhost:${SERVER_PORT}/api/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]


