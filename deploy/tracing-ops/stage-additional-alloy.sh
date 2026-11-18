#!/usr/bin/env bash
set -euo pipefail
export KUBECONFIG="$HOME/.kube/config"
cd "$(dirname "$0")"
mode=${1:?}
case "$mode" in
  apply|check) profile=app-alloy-payment-chatbot ;;
  rollback) profile=app-alloy-search ;;
  *) exit 2 ;;
esac

exec 8>/opt/app/monitoring/operations/environment-operation.lock
flock -w 60 -x 8
exec 9>/opt/app/monitoring/operations/dev.lock
flock -w 60 -x 9

k() { kubectl --request-timeout=15s -n kidly-dev "$@"; }
wait_alloy() { kubectl -n kidly-dev rollout status deployment/tracing-alloy --timeout=120s; }
apply_profile() { k apply --server-side --field-manager=kubectl -k "$1"; }

# This workflow stages only the collector. It must never narrow an active receiver.
markers=/opt/app/dev/operations/tracing-agent
shopt -s nullglob
for marker in "$markers"/*.enabled; do
  case "${marker##*/}" in
    gateway.enabled|order.enabled|search.enabled) ;;
    *) echo 'Remove the additional service agent before changing the receiver' >&2; exit 1 ;;
  esac
done

active=$(k get deployments -l kidly/tracing=enabled -o name)
if [[ -n "$active" && "$active" != deployment.apps/search ]]; then
  echo 'An additional service is already instrumented' >&2
  exit 1
fi

current=$(k get deployment tracing-alloy -o 'jsonpath={.spec.template.spec.containers[?(@.name=="alloy")].env[?(@.name=="TRACING_ALLOWED_SERVICES")].value}')
case "$current" in
  ''|'^(kidly-gateway|kidly-order)$'|'^(kidly-(gateway|order|search))$'|'^(kidly-(gateway|order|search|payment|chatbot))$') ;;
  *) echo 'Unexpected live Alloy service allowlist' >&2; exit 1 ;;
esac

if [[ "$mode" != rollback ]]; then
  wait_alloy
fi
k apply --server-side --field-manager=kubectl --dry-run=server -k "$profile"
if [[ "$mode" == check ]]; then
  exit 0
fi

if apply_profile "$profile" && wait_alloy; then
  printf 'Tracing Alloy ready with profile %s\n' "$profile"
  exit 0
fi

if [[ "$mode" == apply ]]; then
  if apply_profile app-alloy-search && wait_alloy; then
    echo 'Pilot receiver restored' >&2
  else
    echo 'Pilot receiver restoration requires follow-up' >&2
  fi
fi
exit 1
