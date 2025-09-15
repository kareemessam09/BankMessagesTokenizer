# Use OpenJDK 11 as base image for better compatibility
FROM openjdk:11-jre-slim

# Set working directory
WORKDIR /app

# Copy the fat JAR and model files
COPY build/libs/BankMessageTokinizer-1.0.0-all.jar app.jar
COPY src/main/resources/*.* /app/models/

# Create non-root user for security
RUN addgroup --system appgroup && adduser --system --group appuser
RUN chown -R appuser:appgroup /app
USER appuser

# Expose port for REST API (if implemented)
EXPOSE 8080

# Set JVM options for production
ENV JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD java -cp app.jar com.kareemessam09.bankMessagetokinizer.HealthCheckKt || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Labels for metadata
LABEL maintainer="kareem.essam09@example.com"
LABEL version="1.0.0"
LABEL description="Financial NER Library for Banking Messages"
