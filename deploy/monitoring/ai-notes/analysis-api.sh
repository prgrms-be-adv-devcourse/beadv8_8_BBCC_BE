#!/usr/bin/env bash
# Grafana "새 분석 실행" 버튼 처리기(grafana_analysis_api.py)를 다시 띄운다. deploy 유저로 실행한다
#   deploy.sh 가 배포마다 부르고, 서버가 다시 켜지면 crontab @reboot 이 부른다(deploy.sh 가 등록)
# 키는 monitoring-deploy 가 secrets/ 에 쓴 파일을 실행 때마다 읽는다. 명령 인자 · 출력 · 로그에 키를 넣지 않는다
#   GROQ_API_KEY 가 없으면 처리기를 멈춘다(상태 지표가 끊겨 화면의 실행 행이 숨는다)
#   OPENAI_API_KEY 가 있을 때만 OpenAI 를 켠다(유료, 화면에서 고르고 확인 창을 거친다)
set -euo pipefail
cd "$(dirname "$0")/.."
export KUBECONFIG="$HOME/.kube/config"
# crontab 은 PATH 가 짧다. k3s 의 kubectl 은 /usr/local/bin 에 있다
export PATH="/usr/local/bin:/usr/bin:/bin:$PATH"

state=ai-notes-state
pidfile="$state/analysis-api.pid"
port=8786
origin=https://grafana.kidly.kro.kr

# Grafana 알림 웹훅(연결점 ai-analysis)이 보내는 Bearer 토큰. 한 번 만들고 유지한다(바꾸면 Grafana 가 다시 떠야 한다)
# deploy.sh 가 kubectl apply 전에 "token" 으로 불러 Secret ai-analysis-alert 를 맞춘다. 값은 출력 · 명령 인자에 넣지 않는다
token_file="$state/alert-token"
if [[ "${1:-}" == token ]]; then
    mkdir -p -m 700 "$state"
    if [[ ! -s "$token_file" ]]; then
        python3 -c 'import os, secrets, sys; os.umask(0o077); open(sys.argv[1], "w").write(secrets.token_urlsafe(32))' "$token_file"
    fi
    kubectl -n monitoring create secret generic ai-analysis-alert --from-file=AI_ALERT_TOKEN="$token_file" \
        --dry-run=client -o yaml | kubectl -n monitoring apply -f - > /dev/null
    exit 0
fi

stop() {
    if [[ -f "$pidfile" ]]; then
        pid=$(< "$pidfile")
        # 다른 프로세스가 같은 번호를 받았을 수 있어 명령줄을 확인한 뒤에만 끈다
        if [[ "$pid" =~ ^[0-9]+$ ]] && grep -qa grafana_analysis_api.py "/proc/$pid/cmdline" 2>/dev/null; then
            kill "$pid" 2>/dev/null || true
            for _ in $(seq 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
        fi
        rm -f "$pidfile"
    fi
}

stop
if [[ ! -s secrets/GROQ_API_KEY ]]; then
    echo "::warning::GROQ_API_KEY 가 없어 AI 분석 실행 버튼을 끕니다(dev Environment Secret 을 등록하고 monitoring-deploy 를 다시 돌리세요)"
    exit 0
fi

# 부팅 직후에는 k3s 가 늦게 뜬다. 최대 5분 기다린다
grafana_ip=""
for _ in $(seq 60); do
    grafana_ip=$(kubectl -n monitoring get svc grafana -o jsonpath='{.spec.clusterIP}' 2>/dev/null) && [[ -n "$grafana_ip" ]] && break
    sleep 5
done
[[ -n "$grafana_ip" ]] || { echo "::warning::k3s 에서 Grafana 를 찾지 못해 AI 분석 실행 버튼을 띄우지 못했습니다"; exit 0; }
node_ip=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
[[ "$node_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || { echo "::warning::노드 사설 IP 를 읽지 못해 AI 분석 실행 버튼을 띄우지 못했습니다"; exit 0; }

# 모니터링 k3s 의 Traefik(Pod)이 닿도록 노드 사설 IP 에 연다. 보안 그룹이 이 포트를 밖에 열지 않는다
kubectl -n monitoring apply --server-side --force-conflicts -f - > /dev/null <<EOF
apiVersion: discovery.k8s.io/v1
kind: EndpointSlice
metadata:
  name: kidly-ai-analysis
  labels:
    kubernetes.io/service-name: kidly-ai-analysis
addressType: IPv4
endpoints:
  - addresses: [$node_ip]
ports:
  - name: http
    port: $port
    protocol: TCP
EOF

args=(--grafana-url "http://$grafana_ip:3000" --origin "$origin" --host "$node_ip" --port "$port"
      --key-file secrets/GROQ_API_KEY --confirm-free-plan --metrics-file operations/ai-analysis.prom
      --alert-token-file "$token_file" --slack-webhook-file secrets/SLACK_OPS_WEBHOOK_URL)
if [[ -s secrets/OPENAI_API_KEY ]]; then
    args+=(--allow-paid-openai --openai-key-file secrets/OPENAI_API_KEY)
fi

mkdir -p -m 700 "$state"
setsid nohup python3 ai-notes/grafana_analysis_api.py "${args[@]}" > /dev/null 2>&1 < /dev/null &
echo $! > "$pidfile"
sleep 1
if kill -0 "$(< "$pidfile")" 2>/dev/null; then
    echo "AI 분석 실행 처리기를 띄웠습니다($node_ip:$port, OpenAI $([[ -s secrets/OPENAI_API_KEY ]] && echo 켬 || echo 끔))"
else
    rm -f "$pidfile"
    echo "::warning::AI 분석 실행 처리기가 바로 종료됐습니다. 서버에서 ai-notes/analysis-api.sh 를 직접 실행해 확인하세요"
fi
