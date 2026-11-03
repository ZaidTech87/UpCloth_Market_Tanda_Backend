# ---- Stage 1: build the JAR -------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Resolve dependencies first so this layer is cached between builds
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Tests run in CI (mvn test); skipTests here only skips *running* them for a faster image build
RUN mvn -B clean package -DskipTests

# ---- Stage 2: small runtime image ------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as an unprivileged user
RUN groupadd --system app && useradd --system --gid app --home /app app \
 && chown -R app:app /app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app

EXPOSE 8080

# Container-aware heap sizing; JWT_SECRET, DB_URL, R2_* ... come from the environment
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Health endpoint for the platform (Render: set Health Check Path to /api/health)
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]