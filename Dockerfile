# 빌드 스테이지
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
# 빌드할 앱 모듈(apps/ 아래 폴더 이름). --build-arg APP=xxx-service 로 꼭 넘긴다
ARG APP
COPY . .
# 자체 settings가 있는 앱(member-service)은 루트 빌드에 없어 그 폴더의 wrapper로 빌드한다
RUN test -n "${APP}" \
    && if [ -f apps/${APP}/settings.gradle.kts ]; then \
        (cd apps/${APP} && ./gradlew bootJar --no-daemon); \
    else \
        ./gradlew :apps:${APP}:bootJar --no-daemon; \
    fi \
    && cp apps/${APP}/build/libs/*.jar app.jar

# 실행 스테이지
FROM eclipse-temurin:25-jre
WORKDIR /app

# 이미지에 curl 을 넣지 않는다. 빌드할 때마다 Ubuntu 패키지 서버에서 받아야 해서, 그 서버가 안 되면 배포가 막힌다(#1257)
# k3s 는 컨테이너 밖에서 /actuator/health 를 직접 호출하므로 curl 이 필요 없다
# deploy/docker-compose.yml 의 healthcheck 는 curl 을 실행하므로, 이 이미지를 compose 로 직접 띄우면 healthcheck 가 실패한다

# 호스트의 deploy 유저(UID/GID 1001)와 맞춰 볼륨 마운트된 파일을 읽을 수 있게 한다
RUN groupadd -g 1001 app \
    && useradd -u 1001 -g 1001 -M app \
    && mkdir -p /app/logs /app/jwt \
    && chown -R 1001:1001 /app

COPY --from=build --chown=1001:1001 /workspace/app.jar /app/app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
