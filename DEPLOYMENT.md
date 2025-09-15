# Deployment Guide

## 🚀 BankMessageTokinizer Deployment Guide

This guide covers different deployment scenarios for the BankMessageTokinizer library in production environments.

## 📋 Prerequisites

- **Java**: OpenJDK 11 or higher
- **Memory**: Minimum 2GB RAM (4GB recommended for production)
- **Storage**: 1GB free space for model files and logs
- **Network**: Internet access for dependency downloads (build time)

## 🏗️ Build Options

### 1. Library JAR (for integration)
```bash
./gradlew jar
# Output: build/libs/BankMessageTokinizer-1.0.0.jar
```

### 2. Fat JAR (standalone deployment)
```bash
./gradlew fatJar
# Output: build/libs/BankMessageTokinizer-1.0.0-all.jar
```

### 3. Source JAR (for development)
```bash
./gradlew sourcesJar
# Output: build/libs/BankMessageTokinizer-1.0.0-sources.jar
```

### 4. Documentation
```bash
./gradlew dokkaHtml
# Output: build/docs/index.html
```

## 🐳 Docker Deployment

### Build Docker Image
```bash
# Build the fat JAR first
./gradlew fatJar

# Build Docker image
docker build -t financial-ner:1.0.0 .
```

### Run Container
```bash
docker run -d \
  --name financial-ner \
  -p 8080:8080 \
  -e JAVA_OPTS="-Xmx2g -Xms1g" \
  -v $(pwd)/logs:/app/logs \
  financial-ner:1.0.0
```

### Docker Compose (Recommended)
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f bank-message-tokenizer

# Scale service
docker-compose up -d --scale bank-message-tokenizer=3
```

## ☸️ Kubernetes Deployment

### Create Kubernetes Manifests
```yaml
# k8s-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: financial-ner
spec:
  replicas: 3
  selector:
    matchLabels:
      app: financial-ner
  template:
    metadata:
      labels:
        app: financial-ner
    spec:
      containers:
      - name: financial-ner
        image: financial-ner:1.0.0
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "2Gi"
            cpu: "500m"
          limits:
            memory: "4Gi" 
            cpu: "2000m"
        env:
        - name: JAVA_OPTS
          value: "-Xmx2g -Xms1g"
---
apiVersion: v1
kind: Service
metadata:
  name: financial-ner-service
spec:
  selector:
    app: financial-ner
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

### Deploy to Kubernetes
```bash
kubectl apply -f k8s-deployment.yaml
kubectl get pods -l app=financial-ner
kubectl logs -l app=financial-ner
```

## 🏢 Enterprise Deployment

### 1. Load Balancer Configuration
```nginx
# nginx.conf
upstream financial-ner {
    server 10.0.1.10:8080;
    server 10.0.1.11:8080;
    server 10.0.1.12:8080;
}

server {
    listen 80;
    server_name financial-ner.company.com;
    
    location / {
        proxy_pass http://financial-ner;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 2. Monitoring with Prometheus
```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'financial-ner'
    static_configs:
      - targets: ['financial-ner:8080']
    metrics_path: /metrics
```

### 3. Logging Configuration
```yaml
# logback-spring.xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/financial-ner.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/financial-ner.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

## 📊 Performance Tuning

### JVM Optimization
```bash
# Production JVM settings
JAVA_OPTS="-Xmx4g -Xms2g \
           -XX:+UseG1GC \
           -XX:MaxGCPauseMillis=200 \
           -XX:+UseStringDeduplication \
           -XX:+OptimizeStringConcat"
```

### Model Loading Optimization
```kotlin
// Pre-load models for faster startup
val library = FinancialNERLibrary.initialize(
    modelPath = modelPath,
    // ... other parameters
)

// Keep library instance alive for reuse
// Don't recreate for each request
```

### Batch Processing Configuration
```kotlin
// Optimal batch sizes for throughput
val optimalBatchSize = 50 // messages
val messages = inputMessages.chunked(optimalBatchSize)

messages.forEach { batch ->
    val results = library.extractEntitiesBatch(batch)
    // Process results
}
```

## 🔐 Security Configuration

### 1. Runtime Security
```dockerfile
# Use non-root user
RUN adduser --system --group appuser
USER appuser

# Read-only filesystem
VOLUME /tmp
```

### 2. Network Security
```yaml
# Docker network isolation
networks:
  financial-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

### 3. Secrets Management
```bash
# Use external secret management
docker run -d \
  --name financial-ner \
  -e DATABASE_URL_FILE=/run/secrets/db_url \
  --secret db_url \
  financial-ner:1.0.0
```

## 📈 Monitoring & Observability

### Health Checks
```kotlin
// Health check endpoint
@RestController
class HealthController {
    
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf(
            "status" to "UP",
            "version" to "1.0.0",
            "timestamp" to Instant.now().toString()
        ))
    }
}
```

### Metrics Collection
```kotlin
// Micrometer metrics
@Component
class NERMetrics {
    private val processedCounter = Counter.builder("ner.messages.processed").register(meterRegistry)
    private val processingTimer = Timer.builder("ner.processing.duration").register(meterRegistry)
    
    fun recordProcessing(duration: Duration) {
        processedCounter.increment()
        processingTimer.record(duration)
    }
}
```

## 🚀 CI/CD Pipeline

### GitHub Actions Example
```yaml
# .github/workflows/deploy.yml
name: Deploy Financial NER

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'
    
    - name: Build with Gradle
      run: ./gradlew fatJar
    
    - name: Build Docker image
      run: docker build -t financial-ner:${{ github.sha }} .
    
    - name: Deploy to production
      run: |
        docker tag financial-ner:${{ github.sha }} financial-ner:latest
        # Deploy to your infrastructure
```

## 📝 Production Checklist

- [ ] Model files included in deployment package
- [ ] JVM memory settings configured appropriately  
- [ ] Logging configuration in place
- [ ] Health checks implemented
- [ ] Monitoring and metrics enabled
- [ ] Security hardening applied
- [ ] Backup and disaster recovery planned
- [ ] Load testing completed
- [ ] Documentation updated
- [ ] Team training completed

## 🆘 Troubleshooting

### Common Issues

**Out of Memory Errors**
```bash
# Increase heap size
JAVA_OPTS="-Xmx4g -Xms2g"
```

**Model Loading Failures**
```bash
# Verify model files
ls -la src/main/resources/
# Check file permissions
```

**Slow Performance**
```bash
# Enable G1 garbage collector
-XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

### Debug Mode
```bash
# Enable debug logging
java -Dlogging.level.com.kareemessam09=DEBUG -jar app.jar
```
