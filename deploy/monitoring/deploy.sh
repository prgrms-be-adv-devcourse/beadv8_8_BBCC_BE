#!/usr/bin/env bash
# medium(k3s) 서버에서 deploy 유저로 실행한다(~/.kube/config). 워크플로(.github/workflows/monitoring-deploy.yml)가 이 폴더(/opt/app/monitoring)에 매니페스트와 secrets/ 를 올린 뒤 부른다
set -euo pipefail
cd "$(dirname "$0")"
# k3s 의 kubectl 은 KUBECONFIG 가 없으면 root 만 읽는 /etc/rancher/k3s/k3s.yaml 을 읽으려다 실패한다
export KUBECONFIG="$HOME/.kube/config"

k() { kubectl -n monitoring "$@"; }

# 대시보드 ConfigMap 이 커서(약 190KB) client-side apply 의 last-applied 주석 한도(256KB)를 넘을 수 있어 server-side 로 넣는다
# 손으로 바꾼 필드가 있어도 레포 내용으로 되돌린다(--force-conflicts)
kubectl apply --server-side --force-conflicts -k .

# 설정이 틀리면 Pod 가 준비되지 않거나 재시작을 반복해 rollout 이 끝나지 않는다
wait_rollout() {
    local workload=$1 timeout=$2
    if ! k rollout status "$workload" --timeout "$timeout"; then
        echo "::error::$workload 가 $timeout 안에 준비되지 않았습니다"
        k logs "$workload" --all-containers --tail 50 || true
        exit 1
    fi
}
wait_rollout deploy/prometheus 180s
wait_rollout deploy/loki 180s
wait_rollout deploy/grafana 180s
wait_rollout ds/alloy 120s

# GF_SECURITY_ADMIN_PASSWORD 는 Grafana DB 를 처음 만들 때만 쓰인다. Secret 을 바꿔도 반영되도록 배포마다 맞춘다
# 값은 출력하지 않고 stdin 으로만 넘긴다. 워크플로가 printf '%s' 로 만들어 파일 끝에 줄바꿈이 없다
k exec -i deploy/grafana -- grafana cli admin reset-admin-password --password-from-stdin < secrets/GRAFANA_ADMIN_PASSWORD > /dev/null
echo "Grafana 관리자 비밀번호를 Secret 에 맞췄습니다"

# Grafana 는 Ingress 로만 밖에 열려 있어 노드에서 Service ClusterIP 로 바로 부른다
grafana="http://$(k get svc grafana -o jsonpath='{.spec.clusterIP}'):3000"

# Logs Drilldown 표 기본 열. 원본은 grafana/logs-drilldown-default-columns.json 이고 화면에서 바꾼 것은 여기서 파일 내용으로 돌아간다
# server-side apply 라 없으면 만들고 있으면 덮어쓴다. 관리자 계정은 curl 설정(stdin)으로만 넘겨 명령줄 · 출력에 남지 않는다
# 기능 플래그로 켜는 API 가 Grafana 보다 늦게 뜰 수 있어 몇 번 다시 시도한다(apply 라 여러 번 보내도 같다)
password=$(< secrets/GRAFANA_ADMIN_PASSWORD)
password=${password//\\/\\\\}
password=${password//\"/\\\"}
if ! result=$(printf 'user = "admin:%s"\n' "$password" | curl -sS --fail-with-body --retry 5 --retry-all-errors --retry-delay 2 -K - -X PATCH \
    -H 'Content-Type: application/apply-patch+yaml' \
    --data-binary @grafana/logs-drilldown-default-columns.json \
    "$grafana/apis/logsdrilldown.grafana.app/v1beta1/namespaces/default/logsdrilldowndefaultcolumns/loki?fieldManager=deploy&force=true" 2>&1); then
    echo "::error::Logs Drilldown 기본 열을 넣지 못했습니다"
    echo "$result"
    exit 1
fi
echo "Logs Drilldown 기본 열을 맞췄습니다"

# Logs Drilldown 앱은 Grafana 가 뜬 뒤 백그라운드로 받아 온다. 받지 못하면 옛 버전이 남아 기본 열만 조용히 빠지므로 경고로 알린다(배포는 계속)
expected=$(sed -n 's/.*grafana-lokiexplore-app@\([0-9.]*\).*/\1/p' k8s/grafana.yaml)
installed=""
for _ in $(seq 30); do
    installed=$(printf 'user = "admin:%s"\n' "$password" \
        | curl -sS -K - "$grafana/api/plugins/grafana-lokiexplore-app/settings" 2>/dev/null \
        | python3 -c 'import sys, json; print(json.load(sys.stdin)["info"]["version"])' 2>/dev/null) || true
    [[ "$installed" == "$expected" ]] && break
    sleep 2
done
if [[ "$installed" == "$expected" ]]; then
    echo "Logs Drilldown 앱 $installed"
else
    echo "::warning::Logs Drilldown 앱이 ${installed:-알 수 없음} 입니다(기대 $expected). 플러그인 저장소에 닿지 못했을 수 있습니다. 기본 열이 보이지 않으면 monitoring-deploy 를 다시 돌리세요"
fi
