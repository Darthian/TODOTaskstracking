# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Cache dependency layer — only invalidated when pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build jar (tests run separately in CI)
COPY src ./src
RUN mvn package -DskipTests -q && \
    mv target/todo-tracker-*.jar target/app.jar

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=dev", "-jar", "app.jar"]
