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
# 배포마다 이미지가 쌓여 디스크가 차므로 최신 3개만 남기고 지운다
# 다른 환경 컨테이너가 쓰는 이미지는 docker 가 삭제를 거부해서 남는다 (-f 를 쓰지 않는다)
# 지운 이미지로 롤백할 때는 GHCR 에서 다시 받는다
repo="${IMAGE%:*}"
removed=0
for old in $(docker images -a "$repo" --format '{{if eq .Tag "<none>"}}{{.ID}}{{else}}{{.Repository}}:{{.Tag}}{{end}}' | tail -n +4); do
  if docker rmi "$old" > /dev/null 2>&1; then
    removed=$((removed + 1))
  fi
done
echo "오래된 이미지 ${removed}개 정리"
