#!/usr/bin/env bash
# Read-only readiness report. Do not print environment values or Secret data.
set -euo pipefail
export KUBECONFIG="$HOME/.kube/config"

deployment_ready() {
    local namespace=$1 name=$2 replicas
    replicas=$(kubectl -n "$namespace" get deployment "$name" --ignore-not-found \
        -o jsonpath='{.status.availableReplicas}')
    if [[ "$replicas" =~ ^[1-9][0-9]*$ ]]; then
        printf '%s: ready (%s replica(s))\n' "$name" "$replicas"
    else
        printf '%s: not ready\n' "$name"
    fi
}

report_resources() {
    kubectl -n "$1" get "$2" "$3" --ignore-not-found -o name
}

check_app_boundary() {
    kubectl -n edge get ingressroute api --ignore-not-found -o json \
        | python3 -c '
import json, sys
data = sys.stdin.read().strip()
routes = json.loads(data).get("spec", {}).get("routes", []) if data else []
ready = bool(routes) and all(
    any(middleware["name"] == "strip-trace-headers" for middleware in route.get("middlewares", []))
    for route in routes
)
print("public API trace middleware:", "attached" if ready else "check required")
'
    kubectl -n edge get middleware strip-trace-headers --ignore-not-found -o json \
        | python3 -c '
import json, sys
data = sys.stdin.read().strip()
headers = json.loads(data).get("spec", {}).get("headers", {}).get("customRequestHeaders", {}) if data else {}
ready = all(headers.get(name) == "" for name in ("traceparent", "tracestate", "baggage"))
print("public trace header removal:", "configured" if ready else "check required")
'
    local port
    port=$(kubectl -n kidly-dev get service gateway --ignore-not-found \
        -o jsonpath='{.spec.ports[*].nodePort}')
    printf 'Gateway NodePort: %s\n' "${port:-none}"
}

check_agent_file() {
    local jar=/opt/app/kidly-tracing/opentelemetry-javaagent.jar
    local expected=e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149
    if [[ ! -r "$jar" ]]; then
        printf 'Java Agent JAR: absent or unreadable\n'
    elif printf '%s  %s\n' "$expected" "$jar" | sha256sum --check --status; then
        printf 'Java Agent JAR: verified\n'
    else
        printf 'Java Agent JAR: checksum mismatch\n'
    fi
}

report_otlp_port() {
    kubectl get services -A -o json | python3 -c '
import json, sys
services = json.load(sys.stdin)["items"]
matches = [
    service["metadata"]["namespace"] + "/" + service["metadata"]["name"]
    for service in services
    if any(port.get("nodePort") == 30438 for port in service["spec"].get("ports", []))
]
print("OTLP NodePort 30438:", ", ".join(matches) if matches else "unallocated")
'
}

case "${1:-}" in
    app)
        check_app_boundary
        check_agent_file
        deployment_ready kidly-dev gateway
        deployment_ready kidly-dev order
        for workload in gateway order; do
            agent=$(kubectl -n kidly-dev get deployment "$workload" --ignore-not-found \
                -o 'jsonpath={.spec.template.spec.volumes[?(@.name=="kidly-tracing-agent")].name}')
            printf '%s tracing volume: %s\n' "$workload" "${agent:-absent}"
        done
        report_resources kidly-dev service tracing-alloy
        report_resources kidly-dev configmap tracing-tempo-endpoint
        ;;
    monitoring)
        report_otlp_port
        deployment_ready monitoring grafana
        deployment_ready monitoring tempo
        report_resources monitoring service tempo-otlp-private
        report_resources monitoring networkpolicy tempo-otlp-private
        ;;
    *)
        printf 'Expected app or monitoring\n' >&2
        exit 2
        ;;
esac
