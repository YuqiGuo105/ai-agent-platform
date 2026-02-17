# =====================================================
# Multi-stage Dockerfile for agent-telemetry-service
# Build: docker build -f agent-telemetry-service/Dockerfile -t agent-telemetry-service .
# Run from parent directory (ai-agent-platform)
# =====================================================

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install Maven
RUN apk add --no-cache maven

# Copy parent pom and ALL module poms first (for better layer caching)
COPY pom.xml ./pom.xml
COPY agent-common/pom.xml ./agent-common/pom.xml
COPY agent-service/pom.xml ./agent-service/pom.xml
COPY agent-tools-service/pom.xml ./agent-tools-service/pom.xml
COPY agent-telemetry-service/pom.xml ./agent-telemetry-service/pom.xml
COPY agent-knowledge/pom.xml ./agent-knowledge/pom.xml
COPY agent-knowledge-ui/pom.xml ./agent-knowledge-ui/pom.xml

# Download dependencies (cached unless pom changes)
RUN mvn dependency:go-offline -pl agent-telemetry-service -am -q || true

# Copy source code for this module and its dependencies
COPY agent-common/src ./agent-common/src
COPY agent-telemetry-service/src ./agent-telemetry-service/src

# Build the application
RUN mvn clean package -pl agent-telemetry-service -am -DskipTests -q

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy the built jar
COPY --from=builder /build/agent-telemetry-service/target/agent-telemetry-service-*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Default environment variables
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    SPRING_PROFILES_ACTIVE="docker"

# Expose port
EXPOSE 8080

# Health check (uses PORT env var set by Railway)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8080}/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]

