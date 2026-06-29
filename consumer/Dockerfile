# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk-jammy AS builder

ENV DEBIAN_FRONTEND=noninteractive
WORKDIR /workspace

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY . .

RUN set -eux; \
    chmod +x gradlew; \
    ./gradlew :consumer:bootJar --no-daemon; \
    JAR_FILE="$(find consumer/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)"; \
    cp "$JAR_FILE" /tmp/app.jar

FROM eclipse-temurin:25-jre-jammy AS runtime

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"

WORKDIR /app

RUN useradd --create-home --uid 10001 appuser \
    && mkdir -p /app \
    && chown -R appuser:appuser /app

COPY --chown=appuser:appuser --from=builder /tmp/app.jar /app/app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
