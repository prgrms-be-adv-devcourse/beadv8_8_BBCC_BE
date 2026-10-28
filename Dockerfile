# 빌드 스테이지
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
# 빌드할 앱 모듈(apps/ 아래 폴더 이름). 다른 서비스는 --build-arg APP=xxx-service
ARG APP=kidly-app
COPY . .
RUN ./gradlew :apps:${APP}:bootJar --no-daemon \
    && cp apps/${APP}/build/libs/*.jar app.jar

# 실행 스테이지
FROM eclipse-temurin:25-jre
WORKDIR /app

# compose healthcheck 용. JRE 이미지에는 curl이 없다
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 호스트의 deploy 유저(UID/GID 1001)와 맞춰 볼륨 마운트된 파일을 읽을 수 있게 한다
RUN groupadd -g 1001 app \
    && useradd -u 1001 -g 1001 -M app \
    && mkdir -p /app/logs /app/jwt \
    && chown -R 1001:1001 /app

COPY --from=build --chown=1001:1001 /workspace/app.jar /app/app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
