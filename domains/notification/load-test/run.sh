#!/usr/bin/env bash
# 시나리오 하나를 VU 단계별로 돌리고, 도는 동안 Hikari 커넥션 풀을 actuator에서 0.5초마다 읽어 단계마다 한 줄로 남긴다
# 사용: domains/notification/load-test/run.sh <시나리오 파일> [VU 목록]
#   예: domains/notification/load-test/run.sh 1-unread-count-spread.js "10 50 100 200"
# 환경 변수: SERVICE_URL(호스트에서 본 notification-service, 기본 http://localhost:8090), DURATION(기본 30s), AUTH_MODE
set -euo pipefail

SCENARIO=$1
VUS_LIST=${2:-"10 50 100 200"}
SERVICE_URL=${SERVICE_URL:-http://localhost:8090}
DURATION=${DURATION:-30s}
AUTH_MODE=${AUTH_MODE:-header}
DIR=$(cd "$(dirname "$0")" && pwd)
# 컨테이너 안에서 호스트의 서비스로 붙는 주소(Docker Desktop)
K6_BASE_URL=${K6_BASE_URL:-${SERVICE_URL/localhost/host.docker.internal}}

metric() {
  curl -s "$SERVICE_URL/actuator/prometheus" | awk -v name="$1" '$1 ~ "^"name"[{ ]" {v = $2} END {print v}'
}

for vus in $VUS_LIST; do
  samples=$(mktemp)
  acquire_count0=$(metric hikaricp_connections_acquire_seconds_count)
  acquire_sum0=$(metric hikaricp_connections_acquire_seconds_sum)
  ( while true; do echo "$(metric hikaricp_connections_active) $(metric hikaricp_connections_pending)" >> "$samples"; sleep 0.5; done ) &
  sampler=$!

  docker run --rm -v "$DIR:/load-test:ro" -e AUTH_MODE="$AUTH_MODE" -e BASE_URL="$K6_BASE_URL" \
    -e VUS="$vus" -e DURATION="$DURATION" grafana/k6:1.0.0 run --quiet "/load-test/scenarios/$SCENARIO" \
    | awk '/^ +(http_req_duration|http_req_failed|http_reqs)\.\./ {name = $1; sub(/\.+:?$/, "", name)}
           /\{ op:count \}/ {sub(/^ *\{ op:count \}\.*: */, ""); print "  " name ": " $0}
           /checks_succeeded/ {print "  checks: " $2 " " $3 " " $4 " " $5 " " $6}' || true

  kill "$sampler"
  wait "$sampler" 2>/dev/null || true
  acquire_count1=$(metric hikaricp_connections_acquire_seconds_count)
  acquire_sum1=$(metric hikaricp_connections_acquire_seconds_sum)
  pool_max=$(metric hikaricp_connections_max)
  awk -v vus="$vus" -v max="$pool_max" -v c0="$acquire_count0" -v c1="$acquire_count1" \
    -v s0="$acquire_sum0" -v s1="$acquire_sum1" '
    { if ($1 > a) a = $1; if ($2 > p) p = $2; sa += $1; sp += $2; n++ }
    END { c = c1 - c0; s = s1 - s0; printf "[VU %s] Hikari 최대 %d · 사용 중 평균 %.1f 최대 %d · 대기 평균 %.1f 최대 %d · 커넥션 얻기 평균 %.2fms\n",
      vus, max, sa / n, a, sp / n, p, (c > 0 ? s / c * 1000 : 0) }' "$samples"
  rm -f "$samples"
done
