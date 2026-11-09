#!/usr/bin/env bash
# t3.large(단독 k3s)에서 deploy 유저로 실행한다(~/.kube/config). 워크플로(.github/workflows/deploy.yml 의 recommend job)가 이 폴더(/opt/app/recommend-<환경>)에 올린 뒤 부른다
# APP_ENV · RECOMMEND_IMAGE · GHCR_USER · RECOMMEND_NODE_PORT · SPRING_PROFILE · KAFKA_BOOTSTRAP_SERVERS · DB_URL · DB_USERNAME 은 환경 변수로,
# 비밀 세 줄(GHCR 토큰 · DB_PASSWORD · OPENAI_API_KEY, 마지막은 비어도 된다)은 stdin 으로 받는다
set -euo pipefail
: "${APP_ENV:?}" "${RECOMMEND_IMAGE:?}" "${GHCR_USER:?}" "${RECOMMEND_NODE_PORT:?}" "${SPRING_PROFILE:?}"
: "${KAFKA_BOOTSTRAP_SERVERS:?}" "${DB_URL:?}" "${DB_USERNAME:?}"
[[ "$APP_ENV" == dev || "$APP_ENV" == prod ]] || { echo "::error::APP_ENV 는 dev 또는 prod 여야 합니다"; exit 1; }
cd "$(dirname "$0")"
export KUBECONFIG="$HOME/.kube/config"
ns="recommend-$APP_ENV"
k() { kubectl -n "$ns" "$@"; }

IFS= read -r ghcr_token || true
IFS= read -r db_password || true
IFS= read -r openai_key || true
[[ -n "$ghcr_token" && -n "$db_password" ]] || { echo "::error::stdin 으로 GHCR 토큰과 DB_PASSWORD 가 와야 합니다"; exit 1; }

# 이 서버에 k3s 가 없거나 kubeconfig 가 없으면 아무것도 바꾸기 전에 멈춘다
if ! kubectl get --raw /readyz > /dev/null 2>&1; then
  echo "::error::이 서버의 k3s API 에 붙지 못했습니다. k3s 설치와 ~/.kube/config 를 확인하세요(deploy/recommend/README.md)"
  exit 1
fi

# 서버에 장기 토큰을 두지 않는다(#281). 비밀은 명령줄 인자 · 출력에 남기지 않고 deploy 유저만 읽는 임시 폴더에만 둔다
umask 077
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
printf '{"auths":{"ghcr.io":{"auth":"%s"}}}' "$(printf '%s:%s' "$GHCR_USER" "$ghcr_token" | base64 | tr -d '\n')" > "$tmp/dockerconfig"
printf '%s' "$db_password" > "$tmp/DB_PASSWORD"
printf '%s' "$openai_key" > "$tmp/OPENAI_API_KEY"
unset ghcr_token db_password openai_key

kubectl create namespace "$ns" --dry-run=client -o yaml | kubectl apply --server-side -f -
k create secret generic ghcr-pull --type=kubernetes.io/dockerconfigjson --from-file=.dockerconfigjson="$tmp/dockerconfig" \
    --dry-run=client -o yaml | kubectl apply --server-side --force-conflicts -f -
# 비밀이 바뀌어도 Pod 는 다시 뜨지 않는다. 새 값은 다음 이미지 배포(Pod 교체) 때 들어간다
k create secret generic recommend-secrets --from-file=DB_PASSWORD="$tmp/DB_PASSWORD" --from-file=OPENAI_API_KEY="$tmp/OPENAI_API_KEY" \
    --dry-run=client -o yaml | kubectl apply --server-side --force-conflicts -f -
rm -rf "$tmp"

# 손으로 바꾼 필드가 있어도 레포 내용으로 되돌린다(--force-conflicts). replicas 는 매니페스트에 없어 HPA 값이 남는다
sed -e "s|__APP_ENV__|$APP_ENV|g" -e "s|__IMAGE__|$RECOMMEND_IMAGE|" -e "s|__NODE_PORT__|$RECOMMEND_NODE_PORT|" \
    -e "s|__SPRING_PROFILE__|$SPRING_PROFILE|" -e "s|__KAFKA__|$KAFKA_BOOTSTRAP_SERVERS|" \
    -e "s|__DB_URL__|$DB_URL|" -e "s|__DB_USERNAME__|$DB_USERNAME|" recommend.yaml \
  | kubectl apply --server-side --force-conflicts -f -

# 기동이 5분까지 걸릴 수 있다(startupProbe). 준비되지 않으면 원인이 실행 화면에 보이게 남긴다
if ! k rollout status deploy/recommend --timeout 420s; then
  echo "::error::추천 서비스가 420s 안에 준비되지 않았습니다"
  echo "::group::추천 서비스 로그 (마지막 300줄)"
  k logs deploy/recommend --tail 300 || true
  echo "::endgroup::"
  echo "::group::Pod 이벤트"
  k describe pod -l app=recommend | sed -n '/^Events:/,/^$/p' || true
  echo "::endgroup::"
  exit 1
fi
# 스케일링 기준(CPU)을 못 읽으면 HPA 가 늘리지 못한다. 배포는 막지 않고 알린다
if ! k get hpa recommend -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}' | grep -q '[0-9]'; then
  echo "::warning::HPA 가 아직 CPU 사용률을 읽지 못했습니다. 잠시 뒤 kubectl -n $ns get hpa 로 확인하세요(metrics-server)"
fi
