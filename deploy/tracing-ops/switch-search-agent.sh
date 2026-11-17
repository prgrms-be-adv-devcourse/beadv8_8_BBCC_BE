#!/usr/bin/env bash
set -euo pipefail
umask 077
export KUBECONFIG="$HOME/.kube/config"
mode=${1:?}
case "$mode" in apply|rollback) ;; *) exit 2 ;; esac
ops=$(cd "$(dirname "$0")" && pwd)
marker=/opt/app/dev/operations/tracing-agent/search.enabled

exec 8>/opt/app/monitoring/operations/environment-operation.lock
flock -w 60 -x 8
exec 9>/opt/app/monitoring/operations/dev.lock
flock -w 60 -x 9

k() { kubectl -n kidly-dev "$@"; }
ready() { k rollout status "deployment/$1" --timeout=150s; }

flags=$(k get secret/search-env -o 'jsonpath={.data.JAVA_TOOL_OPTIONS}' | base64 --decode)
[[ "$flags" =~ ^-Xmx[0-9]+[mMgG]$ ]] || { echo 'Search JVM flags require review' >&2; exit 1; }

patch() {
  k get deployment/search -o json |
    python3 "$ops/agent-patch.py" --search "--java-tool-options=$flags" "$@" |
    k patch deployment/search --type=strategic --patch-file=/dev/stdin
}

rollback() {
  local mounted
  mounted=$(k get deployment/search -o 'jsonpath={.spec.template.spec.volumes[?(@.name=="kidly-tracing-agent")].name}') || return 1
  if [[ -n "$mounted" ]]; then
    patch --rollback || return 1
  fi
  rm -f -- "$marker" || return 1
  ready search
}

if [[ "$mode" == rollback ]]; then
  rollback
  exit
fi

[[ ! -e "$marker" ]]
allowlist=$(k get deployment/tracing-alloy -o 'jsonpath={.spec.template.spec.containers[?(@.name=="alloy")].env[?(@.name=="TRACING_ALLOWED_SERVICES")].value}')
[[ "$allowlist" == '^(kidly-(gateway|order|search))$' ]] || { echo 'Search Alloy profile is not active' >&2; exit 1; }
ready tracing-alloy
ready search
test -s /opt/app/kidly-tracing/opentelemetry-javaagent.jar
mkdir -p "$(dirname "$marker")"

patch
if ready search && touch "$marker"; then
  echo 'Search Agent ready at the existing 5% sampling rate'
else
  rollback || echo 'Search Agent restoration requires follow-up' >&2
  exit 1
fi
