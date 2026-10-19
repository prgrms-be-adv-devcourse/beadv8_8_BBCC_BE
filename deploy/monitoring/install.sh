#!/usr/bin/env bash
# 모니터링 스택(Prometheus · Loki · Alloy · Grafana) 설치 · 갱신. 여러 번 실행해도 된다.
# 사용법: sudo ./install.sh
# 설정은 매번 이 폴더 것으로 덮어쓰고 컨테이너를 다시 만든다. 비밀(.env)과 데이터(볼륨)는 그대로 둔다
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
    echo "root 권한이 필요합니다. sudo 로 실행하세요." >&2
    exit 1
fi
command -v docker >/dev/null || { echo "docker 가 없습니다. 먼저 설치하세요." >&2; exit 1; }

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
DEST=/opt/monitoring
# 설정을 지우고 다시 복사하므로 복사본(/opt/monitoring)에서 실행하면 자기 설정을 지운다
[[ $SCRIPT_DIR != "$DEST" ]] || { echo "레포의 deploy/monitoring 폴더에서 실행하세요 ($DEST 가 아니라)" >&2; exit 1; }
mkdir -p "$DEST"

# 비밀은 서버에만 둔다. 처음이면 빈 파일을 만들고 채운 뒤 다시 실행하게 한다
if [[ ! -f $DEST/.env ]]; then
    (umask 077 && printf 'GRAFANA_ADMIN_PASSWORD=\nSLACK_OPS_WEBHOOK_URL=\n' > "$DEST/.env")
    echo "$DEST/.env 를 만들었습니다. 두 값을 채운 뒤 다시 실행하세요." >&2
    exit 1
fi
for key in GRAFANA_ADMIN_PASSWORD SLACK_OPS_WEBHOOK_URL; do
    grep -Eq "^$key=.+" "$DEST/.env" || { echo "$DEST/.env 에 $key 값이 없습니다." >&2; exit 1; }
done
# 손으로 고치다 권한이 넓어져도 root 만 읽게 되돌린다
chmod 600 "$DEST/.env"

echo "== 설정 복사"
rm -rf "$DEST/prometheus" "$DEST/loki" "$DEST/alloy" "$DEST/grafana"
cp -r "$SCRIPT_DIR/docker-compose.yml" "$SCRIPT_DIR/prometheus" "$SCRIPT_DIR/loki" "$SCRIPT_DIR/alloy" \
    "$SCRIPT_DIR/grafana" "$DEST/"
# 컨테이너 사용자(nobody 등)가 읽을 수 있게 한다
chmod -R a+rX "$DEST/prometheus" "$DEST/loki" "$DEST/alloy" "$DEST/grafana" "$DEST/docker-compose.yml"

echo "== 컨테이너 다시 만들기"
cd "$DEST"
docker compose -p monitoring --env-file .env pull -q
docker compose -p monitoring --env-file .env up -d --force-recreate --remove-orphans

cat <<MSG

완료. 확인해 보세요:
  curl -s 127.0.0.1:9090/api/v1/targets | grep -o '"health":"[a-z]*"'
  curl -s 127.0.0.1:3100/ready
  curl -s 127.0.0.1:3000/api/health
MSG
