#!/usr/bin/env bash
# t3.large 에서 실행한다. nginx 와 인증서 갱신을 이 서버의 단독 k3s(namespace edge)에 띄운다(nginx.yaml, #1309)
# 사용법: MONITORING_HOST=<모니터링 서버 사설 IP> ./deploy.sh <도메인>
#   예) MONITORING_HOST=10.0.1.23 ./deploy.sh kidly.example
# 설정을 ConfigMap 으로 만들어 nginx -t 로 먼저 검사하고, 통과해야 Pod 를 바꾼다. 틀린 설정으로 80 · 443 이 비지 않게 한다
set -euo pipefail
cd "$(dirname "$0")"
export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"

DOMAIN=${1:-}
if [[ ! $DOMAIN =~ ^[a-z0-9-]+(\.[a-z0-9-]+)+$ ]] || [[ $DOMAIN == api.* || $DOMAIN == dev-api.* || $DOMAIN == grafana.* ]]; then
    echo "사용법: MONITORING_HOST=<모니터링 서버 사설 IP> $0 <루트 도메인>" >&2
    exit 1
fi
if [[ ! ${MONITORING_HOST:-} =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
    echo "MONITORING_HOST 에 모니터링 서버(t3.medium)의 사설 IPv4 를 넣으세요: ${MONITORING_HOST:-(비어 있음)}" >&2
    exit 1
fi
if ! kubectl get --raw /readyz > /dev/null 2>&1; then
    echo "이 서버의 k3s API 에 붙지 못했습니다. KUBECONFIG 를 확인하세요(deploy/k3s/README.md)" >&2
    exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
sed -e "s/__DOMAIN__/$DOMAIN/g" -e "s/__MONITORING_HOST__/$MONITORING_HOST/g" kidly.conf.template > "$work/kidly.conf"
conf="nginx-conf-$(sha256sum < "$work/kidly.conf" | cut -c1-10)"

kubectl create namespace edge --dry-run=client -o yaml | kubectl apply --server-side -f - > /dev/null
kubectl -n edge create configmap "$conf" --from-file=kidly.conf="$work/kidly.conf" --dry-run=client -o yaml \
    | kubectl apply --server-side --force-conflicts -f - > /dev/null

echo "== 설정 검사 (nginx -t, $conf)"
kubectl -n edge delete pod nginx-check --ignore-not-found --wait > /dev/null
kubectl -n edge apply -f - > /dev/null <<POD
apiVersion: v1
kind: Pod
metadata:
  name: nginx-check
spec:
  restartPolicy: Never
  automountServiceAccountToken: false
  containers:
    - name: nginx
      image: nginx:1.28.0
      command: [nginx, -t]
      resources:
        requests: {cpu: 10m, memory: 32Mi}
        limits: {memory: 128Mi}
      volumeMounts:
        - {name: conf, mountPath: /etc/nginx/conf.d, readOnly: true}
        - {name: letsencrypt, mountPath: /etc/letsencrypt, readOnly: true}
  volumes:
    - name: conf
      configMap: {name: $conf}
    - name: letsencrypt
      hostPath: {path: /etc/letsencrypt, type: Directory}
POD
phase=""
for _ in $(seq 120); do
    phase=$(kubectl -n edge get pod nginx-check -o jsonpath='{.status.phase}')
    [[ $phase == Succeeded || $phase == Failed ]] && break
    sleep 1
done
kubectl -n edge logs nginx-check || true
kubectl -n edge delete pod nginx-check --wait=false > /dev/null
if [[ $phase != Succeeded ]]; then
    echo "설정 검사가 통과하지 않았습니다(${phase:-응답 없음}). 떠 있는 nginx 는 그대로 둡니다" >&2
    kubectl -n edge delete configmap "$conf" --ignore-not-found > /dev/null
    exit 1
fi

echo "== 적용"
sed "s/__CONF__/$conf/" nginx.yaml | kubectl apply --server-side --force-conflicts -f - > /dev/null
# 처음 옮길 때: 호스트 nginx 가 80 · 443 을 쥐고 있으면 Pod 는 놓을 때까지 기다린다. 끄는 것은 sudo 라 사람이 한다
if systemctl is-active --quiet nginx 2> /dev/null; then
    cat <<MSG
Pod 가 호스트 nginx 를 기다립니다. 호스트 nginx 와 예전 갱신 타이머를 끄면 1초 안에 이어받습니다:
  sudo systemctl disable --now nginx kidly-certbot-renew.timer
  kubectl -n edge rollout status deploy/nginx
되돌리기: kubectl -n edge scale deploy/nginx --replicas 0 && sudo systemctl enable --now nginx
MSG
    exit 0
fi
if ! kubectl -n edge rollout status deploy/nginx --timeout 120s; then
    echo "nginx 가 120초 안에 준비되지 않았습니다. 호스트 nginx 가 80 · 443 을 쥐고 있으면 기다리는 중입니다(README 「전환」)" >&2
    kubectl -n edge logs deploy/nginx --tail 20 || true
    exit 1
fi
# 지금 쓰는 것 말고 예전 설정 ConfigMap 을 지운다
kubectl -n edge get configmap -o name | grep '^configmap/nginx-conf-' | grep -v "/$conf$" | xargs -r kubectl -n edge delete > /dev/null
echo "nginx 가 준비됐습니다"
