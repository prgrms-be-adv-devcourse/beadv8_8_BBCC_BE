#!/usr/bin/env bash
# t3.large 에서 실행한다. 워크플로(.github/workflows/monitoring-deploy.yml)가 이 폴더(/opt/app/monitoring)에 설정과 .env 를 올린 뒤 부른다
# Alloy 를 이 서버의 단독 k3s(namespace monitoring)에 띄운다(alloy.yaml, #1210)
set -euo pipefail
cd "$(dirname "$0")"
export KUBECONFIG="$HOME/.kube/config"

if ! kubectl get --raw /readyz > /dev/null 2>&1; then
    echo "::error::이 서버의 k3s API 에 붙지 못했습니다. k3s 설치와 ~/.kube/config 를 확인하세요(deploy/k3s/README.md)"
    exit 1
fi
# shellcheck disable=SC1091 # 워크플로가 만든 MONITORING_HOST 한 줄
source .env
[[ "${MONITORING_HOST:-}" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || { echo "::error::.env 의 MONITORING_HOST 가 사설 IPv4 가 아닙니다"; exit 1; }

kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply --server-side -f - > /dev/null
kubectl -n monitoring create configmap alloy-config --from-file=config.alloy --dry-run=client -o yaml \
    | kubectl apply --server-side --force-conflicts -f - > /dev/null
kubectl -n monitoring create configmap alloy-env --from-literal=MONITORING_HOST="$MONITORING_HOST" --dry-run=client -o yaml \
    | kubectl apply --server-side --force-conflicts -f - > /dev/null

# 예전 docker compose 의 Alloy 가 같은 호스트 포트(127.0.0.1:12345)를 쓰므로 먼저 내린다. 새 Pod 가 뜰 때까지 수집이 잠깐(1분 안쪽) 빈다
# docker 를 지운 뒤에는 할 일이 없다
if command -v docker > /dev/null && docker info > /dev/null 2>&1; then
    docker compose -p monitoring down > /dev/null 2>&1 || true
fi
rm -f docker-compose.yml k3s-metrics.yaml
# 예전 지표 읽기 계정(k3s-metrics.yaml). Pod 의 ServiceAccount 로 바꿨다
kubectl delete -n kube-system serviceaccount/kidly-metrics-reader secret/kidly-metrics-reader-token --ignore-not-found > /dev/null
kubectl delete clusterrole/kidly-metrics-reader clusterrolebinding/kidly-metrics-reader --ignore-not-found > /dev/null

hash=$(cat config.alloy .env alloy.yaml | sha256sum | cut -c1-16)
sed "s/__CONFIG_HASH__/$hash/" alloy.yaml | kubectl apply --server-side --force-conflicts -f - > /dev/null

# 설정이 틀리면 재시작을 반복하며 준비되지 않는다
if ! kubectl -n monitoring rollout status deploy/alloy --timeout 120s; then
    echo "::error::alloy 가 120초 안에 준비되지 않았습니다"
    kubectl -n monitoring logs deploy/alloy --tail 50 || true
    kubectl -n monitoring describe pod -l app=alloy | sed -n '/^Events:/,/^$/p' || true
    exit 1
fi
restarts=$(kubectl -n monitoring get pod -l app=alloy -o jsonpath='{.items[*].status.containerStatuses[*].restartCount}')
if [[ "$restarts" =~ [1-9] ]]; then
    echo "::error::alloy 가 재시작했습니다(restartCount $restarts)"
    kubectl -n monitoring logs deploy/alloy --previous --tail 50 || true
    exit 1
fi
echo "Alloy 가 준비됐습니다"
