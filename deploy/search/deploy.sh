#!/usr/bin/env bash
# medium(k3s) 서버에서 deploy 유저로 실행한다(~/.kube/config). 워크플로(.github/workflows/deploy.yml 의 elasticsearch job)가 이 폴더(/opt/app/search)에 올린 뒤 부른다
# ES_IMAGE · GHCR_USER 는 환경 변수로, GHCR 토큰(배포 잡의 GITHUB_TOKEN)은 stdin 으로 받는다
set -euo pipefail
: "${ES_IMAGE:?ES_IMAGE 가 필요합니다}" "${GHCR_USER:?GHCR_USER 가 필요합니다}"
cd "$(dirname "$0")"
# k3s 의 kubectl 은 KUBECONFIG 가 없으면 root 만 읽는 /etc/rancher/k3s/k3s.yaml 을 읽으려다 실패한다
export KUBECONFIG="$HOME/.kube/config"

k() { kubectl -n search "$@"; }

# 서버에 장기 토큰을 두지 않는다(#281). 이번 배포 잡의 토큰으로 pull secret 을 매번 다시 만든다
# 토큰은 명령줄 인자 · 출력에 남기지 않고 deploy 유저만 읽는 임시 파일에만 둔다
token=$(cat)
[[ -n "$token" ]] || { echo "::error::stdin 으로 GHCR 토큰이 와야 합니다"; exit 1; }
umask 077
dockerconfig=$(mktemp)
trap 'rm -f "$dockerconfig"' EXIT
printf '{"auths":{"ghcr.io":{"auth":"%s"}}}' "$(printf '%s:%s' "$GHCR_USER" "$token" | base64 | tr -d '\n')" > "$dockerconfig"
unset token

# Secret 을 넣을 namespace 가 먼저 있어야 한다
kubectl create namespace search --dry-run=client -o yaml | kubectl apply --server-side -f -
k create secret generic ghcr-pull --type=kubernetes.io/dockerconfigjson --from-file=.dockerconfigjson="$dockerconfig" \
    --dry-run=client -o yaml | kubectl apply --server-side --force-conflicts -f -
rm -f "$dockerconfig"

# 손으로 바꾼 필드가 있어도 레포 내용으로 되돌린다(--force-conflicts)
sed "s|__ES_IMAGE__|$ES_IMAGE|" elasticsearch.yaml | kubectl apply --server-side --force-conflicts -f -

# 이미지를 받지 못하거나(ImagePullBackOff) ES 가 준비되지 않으면 rollout 이 끝나지 않는다. 원인이 실행 화면에 보이게 남긴다
if ! k rollout status deploy/elasticsearch --timeout 300s; then
    echo "::error::Elasticsearch 가 300s 안에 준비되지 않았습니다"
    echo "::group::Elasticsearch 로그 (마지막 100줄)"
    k logs deploy/elasticsearch --tail 100 || true
    echo "::endgroup::"
    echo "::group::Pod 이벤트"
    k describe pod -l app=elasticsearch | sed -n '/^Events:/,/^$/p' || true
    echo "::endgroup::"
    exit 1
fi
