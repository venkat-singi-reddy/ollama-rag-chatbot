# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

# Download deps first for better layer caching
RUN apk add --no-cache maven && \
    mvn -B dependency:go-offline -q && \
    mvn -B package -DskipTests -q

# ── Runtime stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the fat JAR produced by the build stage
COPY --from=build /workspace/target/*.jar app.jar

# Spring Boot default port
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
