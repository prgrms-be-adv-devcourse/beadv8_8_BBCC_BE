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

case "${1:-}" in
    app)
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
