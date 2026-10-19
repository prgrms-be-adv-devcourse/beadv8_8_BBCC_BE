#!/usr/bin/env bash
# 서버에서 실행한다. 워크플로(.github/workflows/monitoring-deploy.yml)가 이 폴더(/opt/monitoring)에 설정과 .env 를 올린 뒤 부른다
set -euo pipefail
cd "$(dirname "$0")"

compose() { docker compose -p monitoring --env-file .env "$@"; }

compose pull -q
compose up -d --force-recreate --remove-orphans

# 서비스마다 준비 신호를 기다린다(최대 약 90초). 설정이 틀리면 재시작을 반복하며 준비되지 않는다
wait_ready() {
    local service=$1 url=$2
    for _ in $(seq 45); do
        curl -sf "$url" > /dev/null && return 0
        sleep 2
    done
    echo "::error::$service 가 90초 안에 준비되지 않았습니다 ($url)"
    compose logs "$service" --no-log-prefix --tail 50 || true
    exit 1
}
wait_ready prometheus http://127.0.0.1:9090/-/ready
wait_ready loki http://127.0.0.1:3100/ready
wait_ready alloy http://127.0.0.1:12345/-/ready
wait_ready grafana http://127.0.0.1:3000/api/health

# 크래시 루프 중에 잠깐 떠 있던 것은 준비 신호만으로 못 거른다. --force-recreate 로 새로 만들었으니 재시작 횟수가 0 이어야 한다
# shellcheck disable=SC2046 # 컨테이너 ID 목록을 인자로 펼친다
restarted=$(docker inspect -f '{{.Name}} {{.RestartCount}}' $(compose ps -aq) | awk '$2 > 0 { sub("^/", "", $1); print $1 }')
if [[ -n "$restarted" ]]; then
    echo "::error::모니터링 컨테이너가 재시작했습니다: $(echo $restarted)"
    for container in $restarted; do
        docker logs --tail 50 "$container" 2>&1 || true
    done
    exit 1
fi

# GF_SECURITY_ADMIN_PASSWORD 는 Grafana DB 를 처음 만들 때만 쓰인다. Secret 을 바꿔도 반영되도록 배포마다 맞춘다
# .env 는 워크플로가 KEY='값' 으로 만들어 bash 로 읽어도 값이 바뀌지 않는다. 값은 출력하지 않고 stdin 으로만 넘긴다
. ./.env
printf '%s' "$GRAFANA_ADMIN_PASSWORD" | compose exec -T grafana grafana cli admin reset-admin-password --password-from-stdin > /dev/null
echo "Grafana 관리자 비밀번호를 Secret 에 맞췄습니다"
