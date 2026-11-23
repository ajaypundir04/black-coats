# ==============================================================================
# Multi-stage Dockerfile for Black-Coat API (Java 21/25 Spring Boot)
# Optimized for Render Free Tier (Low RAM & Fast Startup)
# ==============================================================================

# Stage 1: Build the JAR with Maven
FROM maven:3.9.7-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal Distroless / Alpine JRE Runtime
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Security: Run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy built artifact from builder
COPY --from=builder /app/target/*.jar app.jar

# JVM Performance Tuning for 512MB RAM constraints:
# -XX:+UseCompactObjectHeaders: Reduces memory footprint of object headers
# -XX:MaxRAMPercentage=75.0: Keeps heap within free tier container memory limit
# -XX:+UseStringDeduplication: Saves memory on duplicated string tokens
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
