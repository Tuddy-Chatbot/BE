# syntax=docker/dockerfile:1

# BUILD
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew ./
COPY gradle/ ./gradle/
COPY settings.gradle* build.gradle* ./
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies || true
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# RUNTIME
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul SERVER_PORT=8088
WORKDIR /app

# curl 설치
USER root
RUN apt-get update && apt-get install -y curl ca-certificates && rm -rf /var/lib/apt/lists/*

# JAR 복사
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

# 비루트 사용자
RUN useradd -ms /bin/bash appuser && chown -R appuser:appuser /app
USER appuser

EXPOSE 8088

# 헬스체크
HEALTHCHECK --interval=10s --timeout=2s --retries=12 --start-period=60s \
  CMD curl -fsS http://localhost:${SERVER_PORT}/actuator/health | grep -q '"UP"' || exit 1

ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]

