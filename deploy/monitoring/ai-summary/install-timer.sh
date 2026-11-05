#!/usr/bin/env bash
# 기존 서버 관리자가 실행한다. 새 계정/패키지/권한은 만들지 않는다.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo '서버 관리자의 sudo로 실행하세요' >&2; exit 1; }
cd /opt/app/monitoring/ai-summary
# 초기 상태/볼륨은 deploy.sh가 먼저 만들었어야 한다.
[[ -d ../ai-summary-state/dashboards && -d ../operations ]]
install -m 644 kidly-ai-summary.service /etc/systemd/system/
install -m 644 kidly-ai-summary.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now kidly-ai-summary.timer
