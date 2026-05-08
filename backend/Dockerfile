# ──────────────────────────────────────────────────────────────
# Stage 1 — Build the JAR with Maven
# ──────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom first and prefetch deps. This layer is cached as long as
# pom.xml doesn't change → fast rebuilds when only source changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy the source and build the jar (skip tests; they run in CI).
COPY src ./src
RUN mvn clean package -DskipTests -B


# ──────────────────────────────────────────────────────────────
# Stage 2 — Runtime image (JRE only, much smaller)
# ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# docker-cli + docker-cli-buildx so the backend can run
# `docker buildx imagetools inspect <ref>` to read the ExposedPorts of an
# image still in the registry without pulling its layers (used by
# GET /images/inspect-remote).
RUN apk add --no-cache docker-cli docker-cli-buildx

# Copy the fat jar produced by the builder stage.
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
