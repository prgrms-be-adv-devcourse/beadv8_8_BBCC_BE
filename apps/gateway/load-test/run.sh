#!/usr/bin/env bash
# 화면 속도(RATES)를 낮은 값부터 하나씩 capacity.js로 실행하고, 단계마다 처리량 · p95 · 오류율을 한 줄씩 모은다.
# SLO를 처음 넘은 단계에서 멈춘다. 더 올려도 꺾인 지점은 바뀌지 않고 공유 dev 서버만 더 오래 괴롭힌다
set -euo pipefail
cd "$(dirname "$0")"
command -v k6 >/dev/null || { echo "k6가 필요합니다(brew install k6). Docker로 돌리면 Docker Desktop 네트워크가 먼저 막혀 측정이 틀어진다(README)"; exit 1; }

: "${BASE_URL:?BASE_URL을 주세요. 예: BASE_URL=https://dev-api.kidly.kro.kr}"
RATES=${RATES:-"5 10 20 30 40 60 80"}
COOLDOWN=${COOLDOWN:-60}
RUN_ID=${RUN_ID:-$(date +%Y%m%d-%H%M)}
OUT="results/$RUN_ID"
mkdir -p "$OUT"

for rate in $RATES; do
  outputs=()
  if [ -n "${K6_PROMETHEUS_RW_SERVER_URL:-}" ]; then
    outputs=(-o experimental-prometheus-rw --tag "testid=${RUN_ID}-gateway-capacity-r${rate}")
  fi
  echo "== 화면 ${rate}개/초"
  # 임계값을 넘으면 k6가 0이 아닌 값으로 끝나므로 멈추지 않고 결과 줄로 판단한다
  RATE="$rate" SUMMARY_FILE="$OUT/r${rate}.json" k6 run "${outputs[@]+"${outputs[@]}"}" capacity.js 2>&1 \
    | tee "$OUT/r${rate}.log" || true
  line=$(grep -h '^{"rate"' "$OUT/r${rate}.log" | tail -1 || true)
  echo "$line" >> "$OUT/steps.jsonl"
  if [[ "$line" != *'"slo":true'* ]]; then
    echo "화면 ${rate}개/초에서 SLO를 넘었거나 결과가 없어 멈춥니다"
    break
  fi
  sleep "$COOLDOWN"
done

echo "== 단계별 결과 ($OUT/steps.jsonl)"
cat "$OUT/steps.jsonl"
