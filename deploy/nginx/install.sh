#!/usr/bin/env bash
# nginx 설치 + Let's Encrypt 인증서 발급 + 자동 갱신 등록. 여러 번 실행해도 된다.
# 사용법: sudo ./install.sh <도메인> <이메일>   예) sudo ./install.sh kidly.example kidly@example.com
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "사용법: sudo $0 <도메인> <이메일>" >&2
    exit 1
fi
if [[ $EUID -ne 0 ]]; then
    echo "root 권한이 필요합니다. sudo 로 실행하세요." >&2
    exit 1
fi
command -v docker >/dev/null || { echo "docker 가 없습니다. 먼저 설치하세요." >&2; exit 1; }

DOMAIN=$1
EMAIL=$2
if [[ ! $DOMAIN =~ ^[a-z0-9-]+(\.[a-z0-9-]+)+$ ]]; then
    echo "도메인 형식이 잘못됐습니다: $DOMAIN" >&2
    echo "사용법: sudo $0 <도메인> <이메일>" >&2
    exit 1
fi
if [[ $DOMAIN == api.* || $DOMAIN == dev-api.* ]]; then
    echo "루트 도메인만 넣으세요 (예: kidly.store)" >&2
    exit 1
fi
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
CONF=/etc/nginx/conf.d/kidly.conf
WEBROOT=/var/www/certbot
CERTBOT_IMAGE=certbot/certbot:v5.8.0
CERTBOT_RUN="/usr/bin/docker run --rm \
-v /etc/letsencrypt:/etc/letsencrypt \
-v /var/lib/letsencrypt:/var/lib/letsencrypt \
-v $WEBROOT:$WEBROOT \
$CERTBOT_IMAGE"

render() {
    sed "s/__DOMAIN__/$DOMAIN/g" "$SCRIPT_DIR/$1" > "$CONF"
    # 깨진 설정이 남으면 다음 부팅 때 nginx 가 뜨지 않는다
    nginx -t || { rm -f "$CONF"; exit 1; }
    systemctl enable --now nginx
    systemctl reload nginx
}

echo "== nginx 설치"
dnf install -y nginx
mkdir -p "$WEBROOT"

if [[ ! -d /etc/letsencrypt/live/kidly ]]; then
    echo "== 첫 인증서 발급 (api.$DOMAIN, dev-api.$DOMAIN)"
    render acme-only.conf.template
    $CERTBOT_RUN certonly --webroot -w "$WEBROOT" \
        -d "api.$DOMAIN" -d "dev-api.$DOMAIN" --cert-name kidly \
        --email "$EMAIL" --agree-tos --no-eff-email --non-interactive
fi

echo "== https 설정 적용"
render kidly.conf.template

echo "== 자동 갱신 등록 (매일 10:30 KST)"
cat > /etc/systemd/system/kidly-certbot-renew.service <<UNIT
[Unit]
Description=Kidly Let's Encrypt 인증서 갱신
Wants=docker.service
After=docker.service

[Service]
Type=oneshot
ExecStart=$CERTBOT_RUN renew --quiet
ExecStartPost=/usr/bin/systemctl reload nginx
UNIT

# 서버가 18:00~09:00 KST 에 꺼지므로 켜져 있는 시간에 돌린다. 놓치면 부팅 뒤 바로 돈다.
cat > /etc/systemd/system/kidly-certbot-renew.timer <<UNIT
[Unit]
Description=Kidly 인증서 갱신 타이머

[Timer]
OnCalendar=*-*-* 10:30:00 Asia/Seoul
Persistent=true

[Install]
WantedBy=timers.target
UNIT

systemctl daemon-reload
systemctl enable --now kidly-certbot-renew.timer

echo "== 갱신 시험 (실제 인증서는 바뀌지 않음)"
$CERTBOT_RUN renew --dry-run

cat <<MSG

완료. 확인해 보세요:
  curl -I https://api.$DOMAIN/actuator/health
  curl -I https://dev-api.$DOMAIN/actuator/health
  systemctl list-timers kidly-certbot-renew.timer
MSG
