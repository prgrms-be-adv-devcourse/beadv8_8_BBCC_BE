#!/usr/bin/env bash
# Configure only the private Tempo receiver and dedicated Alloy for an initial pilot.
set -euo pipefail
umask 077
cd "$(dirname "$0")/../.."

: "${APP_HOST:?}" "${MONITOR_HOST:?}" "${MONITORING_PRIVATE_IP:?}"
: "${TRACE_SSH_KEY:?}" "${TRACE_KNOWN_HOSTS:?}"
hour=$(TZ=Asia/Seoul date +%H)
(( 10#$hour >= 9 && 10#$hour < 18 )) || { echo 'Outside the operating window'; exit 1; }

remote() {
  local host=$1
  shift
  local command=$1
  ssh -i "$TRACE_SSH_KEY" -o UserKnownHostsFile="$TRACE_KNOWN_HOSTS" \
    -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=8 \
    "deploy@$host" 'export KUBECONFIG="$HOME/.kube/config";' "$command"
}
app() { remote "$APP_HOST" "$@"; }
monitor() { remote "$MONITOR_HOST" "$@"; }

# Never write over an existing receiver, certificate or collector.
monitor 'set -eu
test ! -e /opt/app/monitoring/operations/tempo-private-receiver.enabled
for resource in secret/tracing-tempo-server-tls service/tempo-otlp-private networkpolicy/tempo-otlp-private; do
  existing=$(kubectl -n monitoring get "$resource" --ignore-not-found -o name)
  test -z "$existing"
done
existing=$(kubectl get svc -A -o jsonpath="{range .items[*].spec.ports[?(@.nodePort==30438)]}{.nodePort}{end}")
test -z "$existing"
existing=$(kubectl -n monitoring get deployment tempo -o jsonpath="{.spec.template.spec.volumes[?(@.name==\"receiver-tls\")].name}")
test -z "$existing"
kubectl -n monitoring rollout status deploy/tempo --timeout=60s'
app 'set -eu
for resource in secret/tracing-alloy-client-tls deployment/tracing-alloy service/tracing-alloy networkpolicy/tracing-alloy configmap/tracing-tempo-endpoint; do
  existing=$(kubectl -n kidly-dev get "$resource" --ignore-not-found -o name)
  test -z "$existing"
done
for service in gateway order; do
  test ! -e "/opt/app/dev/operations/tracing-agent/$service.enabled"
done'

addresses='{range .items[*].status.addresses[?(@.type=="InternalIP")]}{.address}{"\n"}{end}'
app_address=$(app "kubectl get nodes -o jsonpath='$addresses'")
tempo_address=$(monitor "kubectl get nodes -o jsonpath='$addresses'")
test "$tempo_address" = "$MONITORING_PRIVATE_IP"
work=$(mktemp -d)
monitor_changed=false
app_changed=false
cleanup() {
  local status=$?
  trap - EXIT
  if (( status != 0 )); then
    if $app_changed; then
      app 'kubectl -n kidly-dev delete deploy/tracing-alloy svc/tracing-alloy networkpolicy/tracing-alloy configmap/tracing-tempo-endpoint secret/tracing-alloy-client-tls --ignore-not-found --timeout=45s' </dev/null ||
        echo 'App collector cleanup requires follow-up'
    fi
    if $monitor_changed; then
      monitor 'set -e
rm -f /opt/app/monitoring/operations/tempo-private-receiver.enabled
kubectl -n monitoring delete service/tempo-otlp-private networkpolicy/tempo-otlp-private --ignore-not-found --timeout=45s
kubectl apply --server-side --force-conflicts -k /opt/app/monitoring/tempo
kubectl -n monitoring rollout status deploy/tempo --timeout=120s
kubectl -n monitoring delete secret/tracing-tempo-server-tls --ignore-not-found' </dev/null ||
        echo 'Tempo receiver cleanup requires follow-up'
    fi
  fi
  rm -rf "$work"
  exit "$status"
}
trap cleanup EXIT
python3 deploy/tracing-ops/private-route.py monitoring --app-source "$app_address" > "$work/monitor.json"
python3 deploy/tracing-ops/private-route.py app --tempo-node "$tempo_address" > "$work/app.json"
kubectl kustomize deploy/tracing-ops/app-alloy > "$work/alloy.yaml"
openssl req -x509 -nodes -newkey rsa:2048 -days 7 -subj /CN=Kidly-tracing-pilot \
  -keyout "$work/ca.key" -out "$work/ca.crt" >/dev/null 2>&1
for role in server client; do
  openssl req -nodes -newkey rsa:2048 -subj "/CN=Kidly-tracing-$role" \
    -keyout "$work/$role.key" -out "$work/$role.csr" >/dev/null 2>&1
  if [[ "$role" == server ]]; then
    printf 'extendedKeyUsage=serverAuth\nsubjectAltName=IP:%s\n' "$tempo_address" > "$work/ext"
  else
    printf 'extendedKeyUsage=clientAuth\n' > "$work/ext"
  fi
  openssl x509 -req -in "$work/$role.csr" -CA "$work/ca.crt" -CAkey "$work/ca.key" \
    -CAcreateserial -days 7 -extfile "$work/ext" -out "$work/$role.crt" >/dev/null 2>&1
done
openssl verify -CAfile "$work/ca.crt" -purpose sslserver -verify_ip "$tempo_address" "$work/server.crt" >/dev/null
openssl verify -CAfile "$work/ca.crt" -purpose sslclient "$work/client.crt" >/dev/null
cp deploy/tracing-ops/verify-transport.py "$work/verify-transport.py"

monitor_changed=true
tar -C "$work" -cf - ca.crt server.crt server.key monitor.json | monitor 'set -eu
umask 077
files=$(mktemp -d)
trap "rm -rf -- \"$files\"" EXIT
tar -xf - -C "$files"
kubectl -n monitoring create secret generic tracing-tempo-server-tls \
  --from-file=ca.crt="$files/ca.crt" --from-file=tls.crt="$files/server.crt" --from-file=tls.key="$files/server.key"
kubectl apply --server-side --force-conflicts -k /opt/app/monitoring/tempo/private-receiver
kubectl -n monitoring rollout status deploy/tempo --timeout=120s
kubectl create -f "$files/monitor.json"
touch /opt/app/monitoring/operations/tempo-private-receiver.enabled'

app_changed=true
tar -C "$work" -cf - ca.crt client.crt client.key app.json alloy.yaml verify-transport.py | app "set -eu
umask 077
files=\$(mktemp -d)
trap 'rm -rf -- \"\$files\"' EXIT
tar -xf - -C \"\$files\"
python3 \"\$files/verify-transport.py\" '$tempo_address' --ca \"\$files/ca.crt\" --cert \"\$files/client.crt\" --key \"\$files/client.key\"
kubectl -n kidly-dev create secret generic tracing-alloy-client-tls \\
  --from-file=ca.crt=\"\$files/ca.crt\" --from-file=tls.crt=\"\$files/client.crt\" --from-file=tls.key=\"\$files/client.key\"
kubectl create -f \"\$files/app.json\"
kubectl apply --server-side -f \"\$files/alloy.yaml\"
kubectl -n kidly-dev rollout status deploy/tracing-alloy --timeout=120s"
echo 'Private receiver and dedicated Alloy ready. Gateway and order were not changed.'
