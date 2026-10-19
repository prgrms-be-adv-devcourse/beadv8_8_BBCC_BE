#!/usr/bin/env bash
# 서버에서 실행한다. 워크플로(.github/workflows/monitoring-deploy.yml)가 이 폴더(/opt/app/monitoring)에 설정과 .env 를 올린 뒤 부른다
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

# Logs Drilldown 표 기본 열. 원본은 grafana/logs-drilldown-default-columns.json 이고 화면에서 바꾼 것은 여기서 파일 내용으로 돌아간다
# server-side apply 라 없으면 만들고 있으면 덮어쓴다. 관리자 계정은 curl 설정(stdin)으로만 넘겨 명령줄 · 출력에 남지 않는다
# 기능 플래그로 켜는 API 가 Grafana 보다 늦게 뜰 수 있어 몇 번 다시 시도한다(apply 라 여러 번 보내도 같다)
password=${GRAFANA_ADMIN_PASSWORD//\\/\\\\}
password=${password//\"/\\\"}
if ! result=$(printf 'user = "admin:%s"\n' "$password" | curl -sS --fail-with-body --retry 5 --retry-all-errors --retry-delay 2 -K - -X PATCH \
    -H 'Content-Type: application/apply-patch+yaml' \
    --data-binary @grafana/logs-drilldown-default-columns.json \
    'http://127.0.0.1:3000/apis/logsdrilldown.grafana.app/v1beta1/namespaces/default/logsdrilldowndefaultcolumns/loki?fieldManager=deploy&force=true' 2>&1); then
    echo "::error::Logs Drilldown 기본 열을 넣지 못했습니다"
    echo "$result"
    exit 1
fi
echo "Logs Drilldown 기본 열을 맞췄습니다"

# Logs Drilldown 앱은 Grafana 가 뜬 뒤 백그라운드로 받아 온다. 받지 못하면 옛 버전이 남아 기본 열만 조용히 빠지므로 경고로 알린다(배포는 계속)
expected=$(sed -n 's/.*grafana-lokiexplore-app@\([0-9.]*\).*/\1/p' docker-compose.yml)
installed=""
for _ in $(seq 30); do
    installed=$(printf 'user = "admin:%s"\n' "$password" \
        | curl -sS -K - http://127.0.0.1:3000/api/plugins/grafana-lokiexplore-app/settings 2>/dev/null \
        | python3 -c 'import sys, json; print(json.load(sys.stdin)["info"]["version"])' 2>/dev/null) || true
    [[ "$installed" == "$expected" ]] && break
    sleep 2
done
if [[ "$installed" == "$expected" ]]; then
    echo "Logs Drilldown 앱 $installed"
else
    echo "::warning::Logs Drilldown 앱이 ${installed:-알 수 없음} 입니다(기대 $expected). 플러그인 저장소에 닿지 못했을 수 있습니다. 기본 열이 보이지 않으면 monitoring-deploy 를 다시 돌리세요"
fi
