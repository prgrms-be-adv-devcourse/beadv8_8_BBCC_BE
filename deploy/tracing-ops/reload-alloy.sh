#!/usr/bin/env bash
set -euo pipefail
export KUBECONFIG="$HOME/.kube/config"

k() { kubectl --request-timeout=12s -n kidly-dev "$@"; }

desired=$1
work=$(mktemp -d)
forward_pid=
patched=false

wait_for_mount() {
  local expected attempt actual
  expected=$(sha256sum < "$1")
  for attempt in {1..36}; do
    if actual=$(k exec "$pod" -c alloy -- cat /etc/alloy/otlp.alloy | sha256sum) &&
      [[ "$expected" == "$actual" ]]; then
      return 0
    fi
    sleep 5
  done
  echo 'Mounted Alloy config did not match expected content' >&2
  return 1
}

replace_config() {
  python3 - "$1" "$2" "$3" <<'PY' | k patch "configmap/$config_map" --type=json --patch-file=/dev/stdin >/dev/null
import json
import sys
from pathlib import Path

previous, replacement, version = sys.argv[1:]
patch = []
if version:
    patch.append({"op": "test", "path": "/metadata/resourceVersion", "value": version})
patch.extend([
    {"op": "test", "path": "/data/otlp.alloy", "value": Path(previous).read_text()},
    {"op": "replace", "path": "/data/otlp.alloy", "value": Path(replacement).read_text()},
])
print(json.dumps(patch))
PY
}

reload() {
  curl --fail --silent --show-error --max-time 15 -X POST "$url/-/reload" >/dev/null &&
    curl --fail --silent --show-error --max-time 10 "$url/-/ready" >/dev/null
}

cleanup() {
  local result=$?
  trap - EXIT
  if (( result != 0 )) && [[ "$patched" == true ]]; then
    if replace_config "$desired" "$work/previous.alloy" '' &&
      wait_for_mount "$work/previous.alloy" && reload; then
      echo 'Previous Alloy configuration restored'
    else
      echo 'Alloy reload failed and automatic restoration requires follow-up' >&2
    fi
  fi
  if [[ -n "$forward_pid" ]]; then
    kill "$forward_pid" 2>/dev/null || true
  fi
  rm -rf -- "$work"
  exit "$result"
}
trap cleanup EXIT

k rollout status deployment/tracing-alloy --timeout=20s
pod=$(k get pods -l app=tracing-alloy -o name)
[[ "$pod" == pod/tracing-alloy-* && "$pod" != *$'\n'* ]]
config_map=$(k get deploy tracing-alloy -o 'jsonpath={.spec.template.spec.volumes[?(@.name=="config")].configMap.name}')
[[ "$config_map" == tracing-alloy-config-* ]]
k get "configmap/$config_map" -o json > "$work/config.json"
version=$(python3 -c 'import json, sys; print(json.load(sys.stdin)["metadata"]["resourceVersion"])' < "$work/config.json")
python3 -c 'import json, sys; sys.stdout.write(json.load(sys.stdin)["data"]["otlp.alloy"])' \
  < "$work/config.json" > "$work/previous.alloy"

kubectl -n kidly-dev port-forward --address=127.0.0.1 "$pod" :12345 >"$work/forward.log" 2>&1 &
forward_pid=$!
port=
for attempt in {1..10}; do
  port=$(sed -n 's/^Forwarding from 127\.0\.0\.1:\([0-9]*\) -> 12345$/\1/p' "$work/forward.log")
  [[ -n "$port" ]] && break
  sleep 1
done
[[ "$port" =~ ^[0-9]+$ ]]
url="http://127.0.0.1:$port"
curl --fail --silent --show-error --max-time 10 "$url/-/ready" >/dev/null

if ! cmp -s "$desired" "$work/previous.alloy"; then
  replace_config "$work/previous.alloy" "$desired" "$version"
  patched=true
fi
wait_for_mount "$desired"
reload
echo 'Tracing Alloy configuration reloaded and ready'
