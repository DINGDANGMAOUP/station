# Multi-stage build for Station Docker Registry Cache

# Stage 1: Build
FROM gradle:9.3.0-jdk21 AS builder

WORKDIR /app

# Copy gradle files
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Copy source code
COPY src ./src

# Build application
RUN gradle clean build -x test --no-daemon

# Stage 2: Runtime
FROM openjdk:21-ea-slim

LABEL maintainer="dingdangmaoup"
LABEL description="Docker Hub cache registry"

# Create app user
RUN groupadd -r station && useradd -r -g station station

# Install dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    ca-certificates && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Create data directory
RUN mkdir -p /data/station && \
    chown -R station:station /app /data/station

# Switch to non-root user
USER station

# Expose ports
EXPOSE 5000 50051

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:5000/actuator/health || exit 1

# JVM options
ENV JAVA_OPTS="-Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.security.egd=file:/dev/./urandom"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
