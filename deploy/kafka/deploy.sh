#!/usr/bin/env bash
# medium(k3s) 서버에서 deploy 유저로 실행한다(~/.kube/config). 워크플로(.github/workflows/deploy.yml 의 kafka job)가 이 폴더(/opt/app/kafka-<환경>)에 올린 뒤 부른다
# APP_ENV(dev|prod) · KAFKA_NODE_PORT · KAFKA_ADVERTISED_HOST(medium 사설 IP)는 환경 변수로 받는다
set -euo pipefail
: "${APP_ENV:?APP_ENV 가 필요합니다}" "${KAFKA_NODE_PORT:?KAFKA_NODE_PORT 가 필요합니다}" "${KAFKA_ADVERTISED_HOST:?KAFKA_ADVERTISED_HOST 가 필요합니다}"
[[ "$APP_ENV" == dev || "$APP_ENV" == prod ]] || { echo "Invalid APP_ENV"; exit 1; }
cd "$(dirname "$0")"
# k3s 의 kubectl 은 KUBECONFIG 가 없으면 root 만 읽는 /etc/rancher/k3s/k3s.yaml 을 읽으려다 실패한다
export KUBECONFIG="$HOME/.kube/config"

k() { kubectl -n "kafka-$APP_ENV" "$@"; }

# 손으로 바꾼 필드가 있어도 레포 내용으로 되돌린다(--force-conflicts)
sed -e "s|__APP_ENV__|$APP_ENV|g" -e "s|__NODE_PORT__|$KAFKA_NODE_PORT|g" -e "s|__ADVERTISED_HOST__|$KAFKA_ADVERTISED_HOST|g" redpanda.yaml \
    | kubectl apply --server-side --force-conflicts -f -

# 브로커가 준비되지 않으면 rollout 이 끝나지 않는다. 원인이 실행 화면에 보이게 남긴다
if ! k rollout status deploy/redpanda --timeout 300s; then
    echo "::error::Redpanda($APP_ENV)가 300s 안에 준비되지 않았습니다"
    echo "::group::Redpanda 로그 (마지막 100줄)"
    k logs deploy/redpanda --tail 100 || true
    echo "::endgroup::"
    echo "::group::Pod 이벤트"
    k describe pod -l app=redpanda | sed -n '/^Events:/,/^$/p' || true
    echo "::endgroup::"
    exit 1
fi
