#!/usr/bin/env bash
# 서버에서 실행한다. 워크플로가 APP_ENV(dev|prod) 와 IMAGE 를 넘긴다
set -euo pipefail
: "${APP_ENV:?APP_ENV 가 필요합니다}" "${IMAGE:?IMAGE 가 필요합니다}"
cd "$(dirname "$0")"
# docker 가 없는 디렉터리를 root 소유로 만들지 않도록 미리 만든다 (컨테이너는 UID 1001 = deploy 로 돈다)
mkdir -p logs
echo "IMAGE=$IMAGE" > image.env
docker compose -p "$APP_ENV" --env-file .env --env-file image.env pull
docker compose -p "$APP_ENV" --env-file .env --env-file image.env up -d --wait
