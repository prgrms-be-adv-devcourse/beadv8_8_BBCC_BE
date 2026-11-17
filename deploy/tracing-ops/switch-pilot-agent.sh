#!/usr/bin/env bash
# Run on the app node with the repository's tracing-ops and k3s files available.
set -euo pipefail
umask 077
export KUBECONFIG="$HOME/.kube/config"
mode=${1:?}
case "$mode" in
  apply|rollback) ;;
  *) exit 2 ;;
esac
ops=$(cd "$(dirname "$0")" && pwd)
markers=/opt/app/dev/operations/tracing-agent
hour=$(TZ=Asia/Seoul date +%H)
(( 10#$hour >= 9 && 10#$hour < 18 )) || { echo 'Outside the operating window'; exit 1; }

# Same lock order as the existing app deployment.
exec 8>/opt/app/monitoring/operations/environment-operation.lock
flock -w 60 -x 8
exec 9>/opt/app/monitoring/operations/dev.lock
flock -w 60 -x 9
k() { kubectl -n kidly-dev "$@"; }

settings() {
  local service=$1 flags
  flags=$(k get "secret/$service-env" -o 'jsonpath={.data.JAVA_TOOL_OPTIONS}' | base64 --decode)
  # The Compose pilot services use one non-secret heap option.
  [[ "$flags" =~ ^-Xmx[0-9]+[mMgG]$ ]] || { echo "$service JVM flags require review" >&2; return 1; }
  printf '%s' "$flags"
}

patch() {
  local service=$1 flags
  shift
  flags=$(settings "$service")
  k get "deploy/$service" -o json |
    python3 "$ops/agent-patch.py" "--java-tool-options=$flags" "$@"
}

wait_ready() {
  k rollout status "deploy/$1" --timeout=180s
}

rollback_service() {
  local service=$1 volume
  volume=$(k get "deploy/$service" -o 'jsonpath={.spec.template.spec.volumes[?(@.name=="kidly-tracing-agent")].name}') || return 1
  if [[ -n "$volume" ]]; then
    patch "$service" --rollback | k patch "deploy/$service" --type=strategic --patch-file=/dev/stdin || return 1
  fi
  wait_ready "$service"
}

if [[ "$mode" == rollback ]]; then
  rm -f -- "$markers/gateway.enabled" "$markers/order.enabled"
  failed=false
  for service in gateway order; do
    rollback_service "$service" || failed=true
  done
  ! $failed
  exit
fi

printf '%s  %s\n' e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149 \
  /opt/app/kidly-tracing/opentelemetry-javaagent.jar | sha256sum --check --status
wait_ready tracing-alloy
for service in order gateway; do
  test ! -e "$markers/$service.enabled"
  wait_ready "$service"
  patch "$service" | k patch "deploy/$service" --dry-run=server --type=strategic --patch-file=/dev/stdin >/dev/null
done

mkdir -p "$markers"
changed=()
cleanup() {
  local status=$?
  trap - EXIT
  if (( status != 0 )); then
    rm -f -- "$markers/gateway.enabled" "$markers/order.enabled"
    local i
    for (( i=${#changed[@]}-1; i>=0; i-- )); do
      rollback_service "${changed[i]}" || echo "${changed[i]} Agent rollback requires follow-up"
    done
  fi
  exit "$status"
}
trap cleanup EXIT

for service in order gateway; do
  changed+=("$service")
  patch "$service" | k patch "deploy/$service" --type=strategic --patch-file=/dev/stdin
  wait_ready "$service"
done
touch "$markers/order.enabled" "$markers/gateway.enabled"
echo 'Agent applied to order and gateway only'
