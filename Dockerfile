# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM gradle:8.7-jdk21 AS builder
WORKDIR /app

# Cache dependencies first
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle/ gradle/
RUN gradle dependencies --no-daemon || true

COPY src/ src/
RUN gradle bootJar --no-daemon -x test

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add non-root user
RUN addgroup -S hrms && adduser -S hrms -G hrms

# Create directories (mounted as volumes in docker-compose)
RUN mkdir -p /data/hrms-sync /app/logs && chown -R hrms:hrms /data/hrms-sync /app/logs

COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown hrms:hrms app.jar

USER hrms
EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
