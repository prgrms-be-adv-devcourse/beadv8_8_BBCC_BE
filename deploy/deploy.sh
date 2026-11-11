#!/usr/bin/env bash
# 서버에서 실행한다. 워크플로가 APP_ENV(dev|prod) 와 이미지 열 개(SEARCH_IMAGE · GATEWAY_IMAGE · RECOMMEND_IMAGE · SETTLEMENT_IMAGE · MEMBER_IMAGE · PAYMENT_IMAGE · PRODUCT_IMAGE · NOTIFICATION_IMAGE · ORDER_IMAGE · COMMUNITY_IMAGE)을 넘긴다
# 검색 서비스 이미지는 profile search, Gateway 이미지는 profile gateway, 추천 이미지는 profile recommend, 정산 이미지는 profile settlement, 회원 이미지는 profile member, 결제 이미지는 profile payment, 상품 이미지는 profile product, 알림 이미지는 profile notification, 주문 이미지는 profile order, 커뮤니티 이미지는 profile community 가 켜진 환경(.env 의 COMPOSE_PROFILES)에서만 받고 띄운다. Elasticsearch 는 t3.medium k3s(deploy/search)에 있다
# .env 의 K3S_PROFILES 에 있는 서비스는 compose 대신 이 서버의 단독 k3s(namespace kidly-<환경>, deploy/k3s)에 하나씩 차례로 올린다(#1142)
set -euo pipefail
: "${APP_ENV:?APP_ENV 가 필요합니다}"
: "${SEARCH_IMAGE:?SEARCH_IMAGE 가 필요합니다}" "${GATEWAY_IMAGE:?GATEWAY_IMAGE 가 필요합니다}"
: "${RECOMMEND_IMAGE:?RECOMMEND_IMAGE 가 필요합니다}"
: "${SETTLEMENT_IMAGE:?SETTLEMENT_IMAGE 가 필요합니다}" "${MEMBER_IMAGE:?MEMBER_IMAGE 가 필요합니다}" "${PAYMENT_IMAGE:?PAYMENT_IMAGE 가 필요합니다}"
: "${PRODUCT_IMAGE:?PRODUCT_IMAGE 가 필요합니다}" "${NOTIFICATION_IMAGE:?NOTIFICATION_IMAGE 가 필요합니다}"
: "${ORDER_IMAGE:?ORDER_IMAGE 가 필요합니다}" "${COMMUNITY_IMAGE:?COMMUNITY_IMAGE 가 필요합니다}"
[[ "$APP_ENV" == dev || "$APP_ENV" == prod ]] || { echo "Invalid APP_ENV"; exit 1; }
cd "$(dirname "$0")"
# docker 가 없는 디렉터리를 root 소유로 만들지 않도록 미리 만든다 (컨테이너는 UID 1001 = deploy 로 돈다)
# Lifecycle operations and application deployment share this environment lock.
mkdir -p ../monitoring/operations
exec 8>../monitoring/operations/environment-operation.lock
flock -x 8
exec 9>"../monitoring/operations/$APP_ENV.lock"
flock -x 9
mode=$(python3 environment-state.py mode "$APP_ENV")
if [[ "$mode" == stopped ]]; then
  echo "::notice::$APP_ENV is intentionally stopped; resume it with environment-operation"
  exit 0
fi
mkdir -p logs
printf 'SEARCH_IMAGE=%s\nGATEWAY_IMAGE=%s\nRECOMMEND_IMAGE=%s\nSETTLEMENT_IMAGE=%s\nMEMBER_IMAGE=%s\nPAYMENT_IMAGE=%s\nPRODUCT_IMAGE=%s\nNOTIFICATION_IMAGE=%s\nORDER_IMAGE=%s\nCOMMUNITY_IMAGE=%s\n' \
  "$SEARCH_IMAGE" "$GATEWAY_IMAGE" "$RECOMMEND_IMAGE" "$SETTLEMENT_IMAGE" "$MEMBER_IMAGE" "$PAYMENT_IMAGE" "$PRODUCT_IMAGE" "$NOTIFICATION_IMAGE" "$ORDER_IMAGE" "$COMMUNITY_IMAGE" > image.env
compose() { docker compose -p "$APP_ENV" --env-file .env --env-file image.env "$@"; }
# 검색이 켜진 환경인지. 꺼진 환경에서는 search-service 가 목록에 없다
search_on=false
services=$(compose config --services)
if grep -qx search-service <<< "$services"; then
  search_on=true
fi
# Gateway 가 켜진 환경인지. 꺼진 환경에서는 gateway 가 목록에 없다
gateway_on=false
if grep -qx gateway <<< "$services"; then
  gateway_on=true
fi
# 추천 서비스가 켜진 환경인지. 꺼진 환경에서는 recommend-service 가 목록에 없다
recommend_on=false
if grep -qx recommend-service <<< "$services"; then
  recommend_on=true
fi
# 정산 서비스가 켜진 환경인지. 꺼진 환경에서는 settlement-service 가 목록에 없다
settlement_on=false
if grep -qx settlement-service <<< "$services"; then
  settlement_on=true
fi
# 회원 서비스가 켜진 환경인지. 꺼진 환경에서는 member-service 가 목록에 없다
member_on=false
if grep -qx member-service <<< "$services"; then
  member_on=true
fi
# 결제 서비스가 켜진 환경인지. 꺼진 환경에서는 payment-service 가 목록에 없다
payment_on=false
if grep -qx payment-service <<< "$services"; then
  payment_on=true
fi
# 상품 서비스가 켜진 환경인지. 꺼진 환경에서는 product-service 가 목록에 없다
product_on=false
if grep -qx product-service <<< "$services"; then
  product_on=true
fi
# 알림 서비스가 켜진 환경인지. 꺼진 환경에서는 notification-service 가 목록에 없다
notification_on=false
if grep -qx notification-service <<< "$services"; then
  notification_on=true
fi
# 주문 서비스가 켜진 환경인지. 꺼진 환경에서는 order-service 가 목록에 없다
order_on=false
if grep -qx order-service <<< "$services"; then
  order_on=true
fi
# 커뮤니티 서비스가 켜진 환경인지. 꺼진 환경에서는 community-service 가 목록에 없다
community_on=false
if grep -qx community-service <<< "$services"; then
  community_on=true
fi
compose pull

# k3s 서비스. 환경 변수 · 이미지 · 메모리 상한은 compose 설정(.env · image.env)에서 그대로 뽑는다(k3s/k3s.py)
# 서비스 하나씩 새 Pod 가 준비된 뒤 옛 Pod 를 내린다. 한꺼번에 뜨는 기동 경합(#840)과 배포 중 503 을 줄인다
k3s_lines=$(python3 environment-state.py k3s "$APP_ENV")
k3s_services=()
[[ -z "$k3s_lines" ]] || mapfile -t k3s_services <<< "$k3s_lines"
if (( ${#k3s_services[@]} > 0 )); then
  export KUBECONFIG="$HOME/.kube/config"
  if ! kubectl get --raw /readyz > /dev/null 2>&1; then
    echo "::error::이 서버의 k3s API 에 붙지 못했습니다. k3s 설치와 ~/.kube/config 를 확인하세요(deploy/k3s/README.md)"
    exit 1
  fi
  : "${DOCKER_CONFIG:?k3s 이미지 pull secret 을 만들 GHCR 로그인(DOCKER_CONFIG)이 필요합니다}"
  ns=$(python3 k3s/k3s.py namespace "$APP_ENV")
  kubectl create namespace "$ns" --dry-run=client -o yaml | kubectl apply --server-side -f - > /dev/null
  python3 k3s/k3s.py pull-secret "$APP_ENV" "$DOCKER_CONFIG" | kubectl apply --server-side --force-conflicts -f - > /dev/null
  # 순서(k3s.py 의 rank): 롤링 서비스 → 두 프로세스가 함께 돌면 안 되는 서비스(SINGLE_INSTANCE) → Gateway(EDGE)
  # SINGLE_INSTANCE 는 compose 의 옛 컨테이너를 먼저 멈추므로 Gateway 가 새 주소로 다시 뜰 때까지 그 서비스만 잠깐 응답하지 못한다(처음 옮길 때 한 번)
  # Gateway 는 다른 서비스를 클러스터 안 이름으로 부르므로 그 서비스들이 모두 뜬 뒤에 올린다
  ordered=()
  for rank in 0 1 2; do
    for line in "${k3s_services[@]}"; do
      read -r _ _ name _ <<< "$line"
      if [[ "$(python3 k3s/k3s.py rank "$APP_ENV" "$name")" == "$rank" ]]; then ordered+=("$line"); fi
    done
  done
  for line in "${ordered[@]}"; do
    read -r service profile name node_port job <<< "$line"
    if python3 k3s/k3s.py single-instance "$APP_ENV" "$name"; then
      compose --profile "$profile" rm --stop --force "$service"
    fi
    # 받을 수 없는 이미지면 Pod 가 ImagePullBackOff 로 남는다. 바꾸기 전에 멈춘다
    image=$(compose --profile "$profile" config --images "$service")
    # GHCR 이미지만 확인한다(kafka-exporter 처럼 공개 이미지는 digest 로 고정돼 있다)
    if [[ "$image" == ghcr.io/* ]]; then
      docker manifest inspect "$image" > /dev/null || { echo "::error::$image 를 GHCR 에서 받을 수 없습니다"; exit 1; }
    fi
    # 손으로 바꾼 필드가 있어도 레포 내용으로 되돌린다(--force-conflicts). replicas 는 매니페스트에 없어 HPA · 끄기 값이 남는다
    compose --profile "$profile" config --format json "$service" \
      | python3 k3s/k3s.py manifest "$APP_ENV" "$service" "$name" "$node_port" "$job" \
      | kubectl apply --server-side --force-conflicts -f - > /dev/null
    if ! kubectl -n "$ns" rollout status "deploy/$name" --timeout 420s; then
      echo "::error::k3s 의 $name 이 420s 안에 준비되지 않았습니다"
      echo "::group::$name 로그 (마지막 300줄)"
      kubectl -n "$ns" logs "deploy/$name" --tail 300 || true
      echo "::endgroup::"
      echo "::group::$name Pod 이벤트"
      kubectl -n "$ns" describe pod -l "app=$name" | sed -n '/^Events:/,/^$/p' || true
      echo "::endgroup::"
      exit 1
    fi
  done
fi
# 앱이 healthy 가 되지 못하면 워크플로가 원인을 뽑을 수 있게 앱 로그를 남기고 실패한다 (.github/scripts/startup-error.sh)
# --remove-orphans: compose 에서 뺀 서비스(t3.medium 으로 옮긴 elasticsearch)의 컨테이너를 지운다. 볼륨은 남는다
# 모든 서비스가 k3s 에 뜨는 환경(COMPOSE_PROFILES 가 빔)은 compose 로 띄울 것이 없다(up 은 no service selected 로 실패한다)
if [[ -n "$services" ]] && ! compose up -d --wait --remove-orphans; then
  echo "::group::앱 로그 (마지막 1000줄)"
  compose logs --no-log-prefix --no-color --tail 1000 || true
  echo "::endgroup::"
  # startup-error.sh 는 위 전체 서비스 로그 구간을 읽는다. 검색 · Gateway · 추천 · 정산 · 회원 · 결제 · 상품 · 알림 · 주문 · 커뮤니티 쪽 원인은 실행 화면에서 본다
  if [[ "$search_on" == true ]]; then
    echo "::group::검색 서비스 로그 (마지막 300줄)"
    compose logs search-service --no-log-prefix --no-color --tail 300 || true
    echo "::endgroup::"
    echo "Elasticsearch 로그는 t3.medium 에서 kubectl -n search logs deploy/elasticsearch 로 본다"
  fi
  if [[ "$gateway_on" == true ]]; then
    echo "::group::Gateway 로그 (마지막 300줄)"
    compose logs gateway --no-log-prefix --no-color --tail 300 || true
    echo "::endgroup::"
  fi
  if [[ "$recommend_on" == true ]]; then
    echo "::group::추천 서비스 로그 (마지막 300줄)"
    compose logs recommend-service --no-log-prefix --no-color --tail 300 || true
    echo "::endgroup::"
  fi
  if [[ "$settlement_on" == true ]]; then
    echo "::group::정산 서비스 로그 (마지막 300줄)"
    compose logs settlement-service --no-log-prefix --no-color --tail 300 || true
    echo "::endgroup::"
  fi
  if [[ "$member_on" == true ]]; then
    echo "::group::회원 서비스 로그 (마지막 300줄)"
    compose logs member-service --no-log-prefix --no-color --tail 300 || true
    echo "::endgroup::"
  fi
  if [[ "$payment_on" == true ]]; then
    echo "::group::결제 서비스 로그 (마지막 300줄)"
    compose logs payment-service --no-log-prefix --no-color --tail 300 || true
    echo "::endgroup::"
  fi
  if [[ "$product_on" == true ]]; then
    echo "::group::상품 서비스 로그 (마지막 300줄)"
    compose logs product-service --no-log-prefix --no-color --tail 300 || true
    echo "::endgroup::"
  fi
  if [[ "$notification_on" == true ]]; then
    echo "::group::알림 서비스 로그 (마지막 300줄)"
    compose logs notification-service --no-log-prefix --no-color --tail 300 || true
    echo "::endgroup::"
  fi
  if [[ "$order_on" == true ]]; then
    echo "::group::주문 서비스 로그 (마지막 300줄)"
    compose logs order-service --no-log-prefix --no-color --tail 300 || true
    echo "::endgroup::"
  fi
  if [[ "$community_on" == true ]]; then
    echo "::group::커뮤니티 서비스 로그 (마지막 300줄)"
    compose logs community-service --no-log-prefix --no-color --tail 300 || true
    echo "::endgroup::"
  fi
  exit 1
fi
# k3s 로 옮겨 간 서비스의 옛 compose 컨테이너를 지운다. --remove-orphans 는 꺼진 profile 의 컨테이너를 남긴다
# 위에서 k3s 의 새 Pod 가 준비됐고 Gateway 도 새 주소로 다시 떴으므로 끊기지 않는다. Gateway 를 옮길 때는 nginx 가 새 NodePort 를 먼저 찾는다(deploy/nginx). 없으면 아무것도 하지 않는다
for line in "${k3s_services[@]}"; do
  read -r service profile _ <<< "$line"
  compose --profile "$profile" rm --stop --force "$service"
done
# 추천만 따로 띄우던 옛 namespace(#1028). 같은 서비스가 kidly-<환경> 에 떴으므로 지운다. 없으면 아무것도 하지 않는다
if (( ${#k3s_services[@]} > 0 )); then
  kubectl delete namespace "recommend-$APP_ENV" --ignore-not-found --wait=false
fi
# Register all current profiles atomically; independent inventory is synchronized by deploy.yml.
python3 environment-state.py targets "$APP_ENV"
# 배포마다 이미지가 쌓여 디스크가 차므로 저장소마다 최신 3개만 남기고 지운다
# 다른 환경 컨테이너가 쓰는 이미지는 docker 가 삭제를 거부해서 남는다 (-f 를 쓰지 않는다)
# 지운 이미지로 롤백할 때는 GHCR 에서 다시 받는다
removed=0
for repo in "${SEARCH_IMAGE%:*}" "${GATEWAY_IMAGE%:*}" "${RECOMMEND_IMAGE%:*}" "${SETTLEMENT_IMAGE%:*}" "${MEMBER_IMAGE%:*}" "${PAYMENT_IMAGE%:*}" "${PRODUCT_IMAGE%:*}" "${NOTIFICATION_IMAGE%:*}" "${ORDER_IMAGE%:*}" "${COMMUNITY_IMAGE%:*}"; do
  for old in $(docker images -a "$repo" --format '{{if eq .Tag "<none>"}}{{.ID}}{{else}}{{.Repository}}:{{.Tag}}{{end}}' | tail -n +4); do
    if docker rmi "$old" > /dev/null 2>&1; then
      removed=$((removed + 1))
    fi
  done
done
echo "오래된 이미지 ${removed}개 정리"
