# Build stage
FROM gradle:8-jdk21 AS builder
WORKDIR /home/gradle/src

# 캐시 무효화를 위한 ARG 추가
ARG CACHEBUST=1

# Use Gradle Wrapper for consistent builds
# Copy wrapper files
COPY --chown=gradle:gradle gradlew build.gradle settings.gradle ./
COPY --chown=gradle:gradle gradle ./gradle

# Download dependencies to a separate layer for caching
RUN ./gradlew dependencies --no-daemon

# Copy source code and build the application
COPY --chown=gradle:gradle src ./src
RUN echo "Cache bust: ${CACHEBUST}"
RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-jammy as final
ENV TZ=Asia/Seoul

# Create a non-root user
RUN groupadd -r spring && useradd -r -g spring spring
WORKDIR /app
RUN mkdir -p /app/upload && chown -R spring:spring /app/upload
USER spring:spring
# Copy the application JAR from the build stage
COPY --from=builder --chown=spring:spring /home/gradle/src/build/libs/*.jar app.jar

# Set up health check to use liveness probe
# liveness: DB 연결과 무관하게 서비스 자체 상태만 체크 (EKS에서 pod 생존 보장)
# readiness는 /actuator/health/readiness로 별도 확인 가능
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health/liveness || exit 1

EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
