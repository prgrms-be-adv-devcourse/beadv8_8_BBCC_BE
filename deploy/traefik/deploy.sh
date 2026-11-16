#!/usr/bin/env bash
# 사용법: MONITORING_HOST=<모니터링 서버 사설 IP> [WEB_PORT=80] [WEBSECURE_PORT=443] ./deploy.sh <루트 도메인>
set -euo pipefail
cd "$(dirname "$0")"
export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"

DOMAIN=${1:-}
WEB_PORT=${WEB_PORT:-80}
WEBSECURE_PORT=${WEBSECURE_PORT:-443}
if [[ ! $DOMAIN =~ ^[a-z0-9-]+(\.[a-z0-9-]+)+$ ]] || [[ $DOMAIN == api.* || $DOMAIN == grafana.* ]]; then
    echo "사용법: MONITORING_HOST=<모니터링 서버 사설 IP> [WEB_PORT=80] [WEBSECURE_PORT=443] $0 <루트 도메인>" >&2
    exit 1
fi
if [[ ! ${MONITORING_HOST:-} =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
    echo "MONITORING_HOST 에 모니터링 서버의 사설 IPv4 를 넣으세요" >&2
    exit 1
fi
if [[ ! $WEB_PORT =~ ^[0-9]+$ || ! $WEBSECURE_PORT =~ ^[0-9]+$ ]]; then
    echo "포트는 숫자여야 합니다" >&2
    exit 1
fi
if ! kubectl get --raw /readyz > /dev/null 2>&1; then
    echo "k3s API 에 붙지 못했습니다. KUBECONFIG 를 확인하세요" >&2
    exit 1
fi
if ! kubectl -n edge get secret kidly-tls > /dev/null 2>&1; then
    echo "edge/kidly-tls 가 없습니다. README의 인증서 Secret 생성 sudo 명령을 실행하세요" >&2
    exit 1
fi

diagnose() {
    kubectl -n kube-system logs job/helm-install-kidly-traefik --all-containers=true --tail=100 >&2 || true
    kubectl -n edge describe pods -l app.kubernetes.io/name=traefik >&2 || true
}

generation() { kubectl -n kube-system get helmchart kidly-traefik -o jsonpath='{.metadata.generation}' 2> /dev/null || true; }
before=$(generation)
applied_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
sed -e "s/__WEB_PORT__/$WEB_PORT/g" -e "s/__WEBSECURE_PORT__/$WEBSECURE_PORT/g" traefik.yaml \
    | kubectl apply --server-side --force-conflicts -f -
for crd in ingressroutes.traefik.io middlewares.traefik.io tlsoptions.traefik.io; do
    found=false
    for _ in $(seq 120); do
        if kubectl get crd "$crd" > /dev/null 2>&1; then found=true; break; fi
        sleep 1
    done
    if [[ $found != true ]] || ! kubectl wait --for=condition=Established "crd/$crd" --timeout=120s; then
        echo "$crd 가 준비되지 않았습니다" >&2
        diagnose
        exit 1
    fi
done
sed -e "s/__DOMAIN__/$DOMAIN/g" -e "s/__MONITORING_HOST__/$MONITORING_HOST/g" routes.yaml \
    | kubectl apply --server-side --force-conflicts -f -

# helm upgrade 는 helm-controller 의 Job 이 비동기로 한다. 값이 바뀌었으면(generation 증가) 이번 적용 뒤에 만든 Job 이 끝나야
# Deployment 가 새 값이다. 그 전에 rollout status 를 보면 옛 Deployment 를 보고 성공으로 끝난다
installed=true
if [[ $(generation) != "$before" ]]; then
    installed=false
    for _ in $(seq 180); do
        created=$(kubectl -n kube-system get job helm-install-kidly-traefik -o jsonpath='{.metadata.creationTimestamp}' 2> /dev/null || true)
        if [[ -n $created && ! $created < $applied_at ]] \
            && kubectl -n kube-system wait --for=condition=Complete job/helm-install-kidly-traefik --timeout=1s > /dev/null 2>&1; then
            installed=true
            break
        fi
        sleep 1
    done
fi
if [[ $installed != true ]] || ! kubectl -n edge rollout status deploy/kidly-traefik --timeout=120s; then
    echo "Traefik 이 새 값으로 준비되지 않았습니다" >&2
    diagnose
    exit 1
fi
kubectl -n edge get pods -l app.kubernetes.io/name=traefik --no-headers
