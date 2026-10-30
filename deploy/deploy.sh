#!/usr/bin/env bash
# 서버에서 실행한다. 워크플로가 APP_ENV(dev|prod) 와 이미지 셋(IMAGE · SEARCH_IMAGE · ES_IMAGE)을 넘긴다
# 검색 이미지 둘은 profile search 가 켜진 환경(.env 의 COMPOSE_PROFILES)에서만 받고 띄운다
set -euo pipefail
: "${APP_ENV:?APP_ENV 가 필요합니다}" "${IMAGE:?IMAGE 가 필요합니다}"
: "${SEARCH_IMAGE:?SEARCH_IMAGE 가 필요합니다}" "${ES_IMAGE:?ES_IMAGE 가 필요합니다}"
cd "$(dirname "$0")"
# docker 가 없는 디렉터리를 root 소유로 만들지 않도록 미리 만든다 (컨테이너는 UID 1001 = deploy 로 돈다)
mkdir -p logs
printf 'IMAGE=%s\nSEARCH_IMAGE=%s\nES_IMAGE=%s\n' "$IMAGE" "$SEARCH_IMAGE" "$ES_IMAGE" > image.env
compose() { docker compose -p "$APP_ENV" --env-file .env --env-file image.env "$@"; }
# 검색이 켜진 환경인지. 꺼진 환경에서는 search-service 가 목록에 없다
search_on=false
services=$(compose config --services)
if grep -qx search-service <<< "$services"; then
  search_on=true
fi
compose pull
# 앱이 healthy 가 되지 못하면 워크플로가 원인을 뽑을 수 있게 앱 로그를 남기고 실패한다 (.github/scripts/startup-error.sh)
if ! compose up -d --wait; then
  echo "::group::앱 로그 (마지막 1000줄)"
  compose logs app --no-log-prefix --no-color --tail 1000 || true
  echo "::endgroup::"
  # startup-error.sh 는 위 앱 로그 구간만 읽는다. 검색 쪽 원인은 실행 화면에서 본다
  if [[ "$search_on" == true ]]; then
    echo "::group::검색 서비스 로그 (마지막 300줄)"
    compose logs search-service --no-log-prefix --no-color --tail 300 || true
    echo "::endgroup::"
    echo "::group::Elasticsearch 로그 (마지막 100줄)"
    compose logs elasticsearch --no-log-prefix --no-color --tail 100 || true
    echo "::endgroup::"
  fi
  exit 1
fi
# 모니터링 수집기 Alloy(deploy/monitoring-agent)가 읽는 수집 대상을 써 둔다. 기동에 실패하면 위에서 끝나 이전 파일이 남는다
# 모니터링 때문에 앱 배포가 실패하면 안 되므로 못 쓰면 경고만 남긴다. Alloy 가 반쯤 쓴 파일을 읽지 않게 .tmp 에 쓰고 옮긴다
port=$(sed -n 's/^APP_PORT=//p' .env | tail -n 1)
targets=../monitoring/targets
if [[ -z "$port" ]]; then
  echo "::warning::.env 에 APP_PORT 가 없어 모니터링 수집 대상을 등록하지 않았습니다"
elif ! { mkdir -p "$targets" \
  && printf '# deploy/deploy.sh 가 배포마다 쓴다. Alloy(deploy/monitoring-agent)가 읽는다\n- targets: ["127.0.0.1:%s"]\n  labels:\n    env: %s\n' "$port" "$APP_ENV" > "$targets/$APP_ENV.tmp" \
  && chmod 644 "$targets/$APP_ENV.tmp" \
  && mv "$targets/$APP_ENV.tmp" "$targets/$APP_ENV.yml"; }; then
  echo "::warning::모니터링 수집 대상($targets/$APP_ENV.yml)을 쓰지 못했습니다"
fi
# 검색 서비스는 Alloy 의 다른 job(kidly-search)이 targets/search/<환경>.yml 을 읽는다. 앱 대시보드 · 알림(job kidly)과 섞이지 않게 나눈다
search_port=$(sed -n 's/^SEARCH_PORT=//p' .env | tail -n 1)
if [[ "$search_on" == true ]]; then
  if [[ -z "$search_port" ]]; then
    echo "::warning::.env 에 SEARCH_PORT 가 없어 검색 서비스 수집 대상을 등록하지 않았습니다"
  elif ! { mkdir -p "$targets/search" \
    && printf '# deploy/deploy.sh 가 배포마다 쓴다. Alloy(deploy/monitoring-agent)가 읽는다\n- targets: ["127.0.0.1:%s"]\n  labels:\n    env: %s\n' "$search_port" "$APP_ENV" > "$targets/search/$APP_ENV.tmp" \
    && chmod 644 "$targets/search/$APP_ENV.tmp" \
    && mv "$targets/search/$APP_ENV.tmp" "$targets/search/$APP_ENV.yml"; }; then
    echo "::warning::모니터링 수집 대상($targets/search/$APP_ENV.yml)을 쓰지 못했습니다"
  fi
fi
# 배포마다 이미지가 쌓여 디스크가 차므로 저장소마다 최신 3개만 남기고 지운다
# 다른 환경 컨테이너가 쓰는 이미지는 docker 가 삭제를 거부해서 남는다 (-f 를 쓰지 않는다)
# 지운 이미지로 롤백할 때는 GHCR 에서 다시 받는다
removed=0
for repo in "${IMAGE%:*}" "${SEARCH_IMAGE%:*}" "${ES_IMAGE%:*}"; do
  for old in $(docker images -a "$repo" --format '{{if eq .Tag "<none>"}}{{.ID}}{{else}}{{.Repository}}:{{.Tag}}{{end}}' | tail -n +4); do
    if docker rmi "$old" > /dev/null 2>&1; then
      removed=$((removed + 1))
    fi
  done
done
echo "오래된 이미지 ${removed}개 정리"
