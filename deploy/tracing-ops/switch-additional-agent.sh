#!/usr/bin/env bash
set -euo pipefail
umask 077
export KUBECONFIG="$HOME/.kube/config"
service=${1:?}
case "$service" in payment|chatbot) ;; *) exit 2 ;; esac
mode=${2:?}
case "$mode" in apply|rollback) ;; *) exit 2 ;; esac
ops=$(cd "$(dirname "$0")" && pwd)
marker="/opt/app/dev/operations/tracing-agent/$service.enabled"

exec 8>/opt/app/monitoring/operations/environment-operation.lock
flock -w 60 -x 8
exec 9>/opt/app/monitoring/operations/dev.lock
flock -w 60 -x 9

k() { kubectl -n kidly-dev "$@"; }
ready() { k rollout status "deployment/$1" --timeout=150s; }

flags=$(k get "secret/$service-env" -o 'jsonpath={.data.JAVA_TOOL_OPTIONS}' | base64 --decode)
[[ "$flags" =~ ^-Xmx[0-9]+[mMgG]$ ]] || { echo 'Selected service JVM flags require review' >&2; exit 1; }

patch() {
  k get "deployment/$service" -o json |
    python3 "$ops/agent-patch.py" --service="$service" "--java-tool-options=$flags" "$@" |
    k patch "deployment/$service" --type=strategic --patch-file=/dev/stdin
}

rollback() {
  local mounted
  mounted=$(k get "deployment/$service" -o 'jsonpath={.spec.template.spec.volumes[?(@.name=="kidly-tracing-agent")].name}') || return 1
  if [[ -n "$mounted" ]]; then
    patch --rollback || return 1
  fi
  rm -f -- "$marker" || return 1
  ready "$service"
}

if [[ "$mode" == rollback ]]; then
  rollback
  exit
fi

if [[ -e "$marker" ]]; then
  echo 'Selected service Agent is already enabled. Run rollback if the marker is stale.' >&2
  exit 1
fi
allowlist=$(k get deployment/tracing-alloy -o 'jsonpath={.spec.template.spec.containers[?(@.name=="alloy")].env[?(@.name=="TRACING_ALLOWED_SERVICES")].value}')
[[ "$allowlist" == '^(kidly-(gateway|order|search|payment|chatbot))$' ]] || { echo 'Selected service Alloy profile is not active' >&2; exit 1; }
ready tracing-alloy
ready "$service"
test -s /opt/app/kidly-tracing/opentelemetry-javaagent.jar
mkdir -p "$(dirname "$marker")"

patch
if ready "$service" && touch "$marker"; then
  echo 'Selected service Agent ready at the existing 5% sampling rate'
else
  rollback || echo 'Selected service Agent restoration requires follow-up' >&2
  exit 1
fi
