#!/usr/bin/env bash
# 서버에서 실행한다. 워크플로가 APP_ENV(dev|prod) 와 IMAGE 를 넘긴다
set -euo pipefail
: "${APP_ENV:?APP_ENV 가 필요합니다}" "${IMAGE:?IMAGE 가 필요합니다}"
cd "$(dirname "$0")"
# docker 가 없는 디렉터리를 root 소유로 만들지 않도록 미리 만든다 (컨테이너는 UID 1001 = deploy 로 돈다)
mkdir -p logs
echo "IMAGE=$IMAGE" > image.env
docker compose -p "$APP_ENV" --env-file .env --env-file image.env pull
# 앱이 healthy 가 되지 못하면 워크플로가 원인을 뽑을 수 있게 앱 로그를 남기고 실패한다 (.github/scripts/startup-error.sh)
if ! docker compose -p "$APP_ENV" --env-file .env --env-file image.env up -d --wait; then
  echo "::group::앱 로그 (마지막 1000줄)"
  docker compose -p "$APP_ENV" --env-file .env --env-file image.env logs app --no-log-prefix --no-color --tail 1000 || true
  echo "::endgroup::"
  exit 1
fi
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
