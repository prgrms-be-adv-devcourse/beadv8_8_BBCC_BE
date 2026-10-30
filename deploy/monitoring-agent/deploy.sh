#!/usr/bin/env bash
# t3.large 에서 실행한다. 워크플로(.github/workflows/monitoring-deploy.yml)가 이 폴더(/opt/app/monitoring)에 설정과 .env 를 올린 뒤 부른다
set -euo pipefail
cd "$(dirname "$0")"

compose() { docker compose -p monitoring --env-file .env "$@"; }

compose pull -q
# 옛 스택(prometheus · loki · grafana)과 같은 프로젝트 이름이라 --remove-orphans 가 그 컨테이너를 지운다
compose up -d --force-recreate --remove-orphans

# 준비 신호를 기다린다(최대 약 90초). 설정이 틀리면 재시작을 반복하며 준비되지 않는다
url=http://127.0.0.1:12345/-/ready
ready=false
for _ in $(seq 45); do
    curl -sf "$url" > /dev/null && { ready=true; break; }
    sleep 2
done
if [[ $ready != true ]]; then
    echo "::error::alloy 가 90초 안에 준비되지 않았습니다 ($url)"
    compose logs alloy --no-log-prefix --tail 50 || true
    exit 1
fi

# 크래시 루프 중에 잠깐 떠 있던 것은 준비 신호만으로 못 거른다. --force-recreate 로 새로 만들었으니 재시작 횟수가 0 이어야 한다
# shellcheck disable=SC2046 # 컨테이너 ID 목록을 인자로 펼친다
restarted=$(docker inspect -f '{{.Name}} {{.RestartCount}}' $(compose ps -aq) | awk '$2 > 0 { sub("^/", "", $1); print $1 }')
if [[ -n "$restarted" ]]; then
    echo "::error::모니터링 컨테이너가 재시작했습니다: ${restarted//$'\n'/ }"
    for container in $restarted; do
        docker logs --tail 50 "$container" 2>&1 || true
    done
    exit 1
fi
echo "Alloy 가 준비됐습니다"
