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
if [[ $DOMAIN == api.* || $DOMAIN == dev-api.* || $DOMAIN == grafana.* ]]; then
    echo "루트 도메인만 넣으세요 (예: kidly.store)" >&2
    exit 1
fi
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
CONF=/etc/nginx/conf.d/kidly.conf
WEBROOT=/var/www/certbot
CERTBOT_IMAGE=certbot/certbot:v5.8.0
# 인증서는 ZeroSSL 에서 받는다. kro.kr 은 공용 접미사 목록(PSL)에 없어 Let's Encrypt 의
# "등록 도메인당 주 50장" 한도를 모든 kro.kr 사용자와 나눠 쓰므로 자주 막힌다
ACME_SERVER=https://acme.zerossl.com/v2/DV90
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

# 인증서가 없으면 발급용 설정으로, 있으면 지금 설정으로 80 을 열어 둔 채 발급 · 이름 추가를 한다
# --expand: 이름이 늘었으면 다시 받는다 / --keep-until-expiring: 그대로면 아무것도 하지 않는다
if [[ -d /etc/letsencrypt/live/kidly ]]; then
    render kidly.conf.template
else
    render acme-only.conf.template
fi
# ZeroSSL 계정이 서버에 없으면(새 서버) EAB 키로 계정을 만든다. 키는 ZeroSSL 대시보드 > Developer 에서 만든다
EAB_ARGS=()
if ! compgen -G "/etc/letsencrypt/accounts/acme.zerossl.com/v2/DV90/*" > /dev/null; then
    if [[ -z ${ZEROSSL_EAB_KID:-} || -z ${ZEROSSL_EAB_HMAC_KEY:-} ]]; then
        echo "서버에 ZeroSSL 계정이 없습니다. EAB 키를 넘겨 다시 실행하세요:" >&2
        echo "  sudo ZEROSSL_EAB_KID=... ZEROSSL_EAB_HMAC_KEY=... $0 $DOMAIN $EMAIL" >&2
        exit 1
    fi
    EAB_ARGS=(--eab-kid "$ZEROSSL_EAB_KID" --eab-hmac-key "$ZEROSSL_EAB_HMAC_KEY")
fi
echo "== 인증서 발급 · 이름 추가 (api.$DOMAIN, dev-api.$DOMAIN, grafana.$DOMAIN)"
$CERTBOT_RUN certonly --webroot -w "$WEBROOT" --server "$ACME_SERVER" "${EAB_ARGS[@]}" \
    -d "api.$DOMAIN" -d "dev-api.$DOMAIN" -d "grafana.$DOMAIN" --cert-name kidly \
    --expand --keep-until-expiring \
    --email "$EMAIL" --agree-tos --no-eff-email --non-interactive

echo "== https 설정 적용"
render kidly.conf.template

echo "== 자동 갱신 등록 (매일 10:30 KST)"
cat > /etc/systemd/system/kidly-certbot-renew.service <<UNIT
[Unit]
Description=Kidly 인증서 갱신 (ZeroSSL)
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
  curl -I https://grafana.$DOMAIN/api/health
  systemctl list-timers kidly-certbot-renew.timer
MSG
