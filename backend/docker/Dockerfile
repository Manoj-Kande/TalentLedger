# ============================================================
# TalentLedger — Multi-stage Production Dockerfile
# Layered JAR for efficient caching
# ============================================================

# ── Stage 1: Build (Maven + JDK 21) ────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy POM first for dependency caching
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B dependency:go-offline -DskipTests

# Copy source and build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B package -DskipTests -pl !:spotless

# ── Stage 2: Runtime (JRE 21 slim) ────────────────────────
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="talentledger"
LABEL description="TalentLedger Contact Intelligence Platform"

# Create non-root user (security best practice)
RUN groupadd -r talentledger && useradd -r -g talentledger -d /app -s /sbin/nologin talentledger

WORKDIR /app

# Copy layered JAR from builder
COPY --from=builder /build/target/talentledger-backend-*.jar app.jar

# Create upload temp directory
RUN mkdir -p /app/uploads && chown -R talentledger:talentledger /app

# Switch to non-root user
USER talentledger

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM flags (container-aware — ADR)
ENV JAVA_OPTS=" \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseZGC \
    -XX:+ZGenerational \
    -Djava.security.egd=file:/dev/./urandom \
    -Duser.timezone=UTC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
