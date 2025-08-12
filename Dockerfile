FROM openjdk:21-jdk-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Copy source code
COPY src src

# Make gradlew executable
RUN chmod +x ./gradlew

# Build the application (skip tests for faster Docker builds)
RUN ./gradlew build -x test -x integrationTest

# Copy the built JAR
RUN cp build/libs/data-processing-service-1.0.0.jar app.jar

# Create directories
RUN mkdir logs
RUN mkdir -p /models/stanford-corenlp

# Set JVM options for NLP processing
ENV JAVA_OPTS="-Xmx2g -XX:+UseG1GC -Djava.awt.headless=true"

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]