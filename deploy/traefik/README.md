# Traefik 엣지

t3.large 의 k3s에서 TLS 종료와 라우팅을 맡습니다(#1423). 1단계에서는 테스트 포트만 쓰고 nginx가 80 · 443을 유지합니다.

| 역할 | 설정 |
| --- | --- |
| TLS 종료 | TLSOption `default` 와 IngressRoute의 `tls` |
| Host 라우팅 | IngressRoute. api는 Gateway, grafana는 모니터링 서버 |
| 클라이언트 IP | entrypoint의 forwardedHeaders 신뢰 목록 비움. hostPort의 portmap DNAT가 원본 IP 보존 |
| HTTP→HTTPS | `http-redirect` 라우터 |
| 추적 헤더 제거 | `strip-trace-headers` 미들웨어 |
| 요청 본문 10MB | `limit-request-body`(Buffering, 넘으면 413). SSE 라우트(`Accept: text/event-stream` 또는 `…/stream`)에는 걸지 않는다. v3.7 Buffering은 응답도 모아 보내 SSE가 끊긴 것처럼 보인다 |

모르는 SNI와 IP 직접 접속은 TLS `sniStrict`가 핸드셰이크를 거부합니다. HTTP의 모르는 Host는 Traefik 404입니다. nginx의 444와 다르지만 앱에는 닿지 않습니다. 기본 인증서는 SNI 일치로 취급되지 않아 TLSStore 대신 각 IngressRoute에 Secret을 둡니다.

k3s의 `--disable traefik`을 유지하고 번들 차트를 HelmChart로 설치합니다. k3s 재시작 없이 같은 차트와 이미지를 씁니다. `kidly-` 이름으로 내장 addon과 구분합니다. hostNetwork는 1024 미만 포트에 root가 필요하고 ServiceLB는 k3s 재시작이 필요해 hostPort를 씁니다. 메모리 request는 64Mi, PriorityClass는 `kidly-edge`입니다.

앱과 따로 배포합니다. 엣지는 드물게 바뀌고 앱 배포 실패가 엣지에 번지지 않게 합니다. 테스트 포트와 80/443 전환 시점은 사람이 고릅니다. k3s를 올리면 `traefik.yaml`의 두 차트 URL과 이미지 태그를 새 k3s 값으로 바꿔 다시 배포합니다. 이미지 태그는 차트 기본값이 아니라 k3s 내장 매니페스트가 덮어쓰는 값입니다. 차트 기본값(`v3.7.1`)은 미러에 없어 `ImagePullBackOff`가 났습니다.

```bash
sudo ls /var/lib/rancher/k3s/server/static/charts/   # 차트 파일 이름
curl -sL "https://raw.githubusercontent.com/k3s-io/k3s/$(k3s --version | awk 'NR==1{print $3}' | sed 's/+/%2B/')/manifests/traefik.yaml" | grep -A1 'repository:'   # 이미지 태그
```

## 1단계 되돌리기

nginx는 건드리지 않았습니다. 서버의 이 폴더에서 Traefik만 지웁니다. HelmChart 삭제 시 helm-controller가 uninstall Job을 실행합니다.

```bash
export KUBECONFIG=$HOME/.kube/config   # deploy 유저의 kubectl 은 이 값이 있어야 붙는다(deploy.sh 는 스스로 넣는다)
kubectl delete -f routes.yaml --ignore-not-found
kubectl -n kube-system delete helmchart kidly-traefik kidly-traefik-crd --ignore-not-found
kubectl -n edge delete ingressroute,service,deployment whoami --ignore-not-found
kubectl -n edge delete secret kidly-tls --ignore-not-found
```

## 1단계: 테스트 포트에 띄우기

1. ec2-user로 인증서 Secret을 만듭니다. certbot이 갱신해도 Secret은 바뀌지 않습니다. 현재 인증서는 2027-02-14 만료라 3단계 전까지 충분하지만 늦어지면 갱신 후 이 명령을 다시 실행합니다.

   ```bash
   sudo k3s kubectl -n edge create secret tls kidly-tls --cert=/etc/letsencrypt/live/kidly/fullchain.pem --key=/etc/letsencrypt/live/kidly/privkey.pem --dry-run=client -o yaml | sudo k3s kubectl apply -f -
   ```

2. 내 PC에서 폴더를 올립니다. 브랜치는 배포할 ref로 바꿉니다.

   ```bash
   BRANCH='<브랜치>'
   git archive "$BRANCH" deploy/traefik | ssh <deploy 유저>@<탄력적 IP> 'rm -rf ~/kidly-traefik && mkdir ~/kidly-traefik && tar -x -C ~/kidly-traefik'
   ```

3. 서버에서 deploy 유저로 실행합니다. 모니터링 서버 보안 그룹은 이 서버에서 오는 80을 받아야 합니다.

   ```bash
   cd ~/kidly-traefik/deploy/traefik
   MONITORING_HOST=<모니터링 서버 사설 IP> WEB_PORT=18080 WEBSECURE_PORT=18443 ./deploy.sh <도메인>
   ```

4. 서버에서 확인합니다. `127.0.0.1`은 portmap DNAT를 타지 않을 수 있어 노드 사설 IP를 씁니다. `routes.yaml`만 바뀐 배포는 `deploy.sh`가 바로 끝나고 Traefik이 몇 초 뒤에 읽으므로 잠깐 기다린 뒤 확인합니다.

   ```bash
   DOMAIN=<도메인>
   NODE_IP=<노드 사설 IP>
   curl --resolve "api.$DOMAIN:18443:$NODE_IP" -s -o /dev/null -w '%{http_code}\n' "https://api.$DOMAIN:18443/api/v1/products?page=0&size=1" # 200
   curl --resolve "api.$DOMAIN:18443:$NODE_IP" -i "https://api.$DOMAIN:18443/actuator/prometheus" # 404 ProblemDetail
   curl --resolve "grafana.$DOMAIN:18443:$NODE_IP" -i "https://grafana.$DOMAIN:18443/api/health" # 200
   curl --resolve "api.$DOMAIN:18080:$NODE_IP" -I "http://api.$DOMAIN:18080/" # 301
   openssl s_client -connect "$NODE_IP:18443" -servername "unknown.$DOMAIN" </dev/null # 핸드셰이크 실패
   openssl s_client -connect "$NODE_IP:18443" -noservername </dev/null # 핸드셰이크 실패
   curl -vI --http2 --resolve "api.$DOMAIN:18443:$NODE_IP" "https://api.$DOMAIN:18443/" # h2 협상
   kubectl -n edge top pod
   ```

5. 임시 whoami로 헤더를 확인합니다. 위조한 IP가 아닌 실제 클라이언트 IP가 전달되고 `traceparent`가 없어야 합니다.

   ```bash
   kubectl apply -f - <<EOF
   apiVersion: apps/v1
   kind: Deployment
   metadata: {name: whoami, namespace: edge}
   spec:
     replicas: 1
     selector: {matchLabels: {app: whoami}}
     template:
       metadata: {labels: {app: whoami}}
       spec:
         containers:
           - name: whoami
             image: traefik/whoami:v1.11.0
             ports: [{containerPort: 80}]
             resources: {requests: {cpu: 5m, memory: 16Mi}}
   ---
   apiVersion: v1
   kind: Service
   metadata: {name: whoami, namespace: edge}
   spec:
     selector: {app: whoami}
     ports: [{port: 80, targetPort: 80}]
   ---
   apiVersion: traefik.io/v1alpha1
   kind: IngressRoute
   metadata: {name: whoami, namespace: edge}
   spec:
     entryPoints: [websecure]
     routes:
       - match: 'Host(\`api.$DOMAIN\`) && PathPrefix(\`/__whoami\`)'
         kind: Rule
         middlewares: [{name: strip-trace-headers}]
         services: [{name: whoami, port: 80}]
     tls: {secretName: kidly-tls}
   EOF
   kubectl -n edge rollout status deploy/whoami --timeout=120s
   curl --resolve "api.$DOMAIN:18443:$NODE_IP" -H 'X-Real-IP: 203.0.113.10' -H 'X-Forwarded-For: 203.0.113.10' -H 'traceparent: 00-0123456789abcdef0123456789abcdef-0123456789abcdef-01' "https://api.$DOMAIN:18443/__whoami"
   kubectl -n edge delete ingressroute,service,deployment whoami
   ```

## 2단계 되돌리기

Traefik을 테스트 포트로 다시 배포하고 nginx를 1개로 올립니다. nginx Pod는 호스트 네트워크로 80 · 443을 선언하므로, Traefik이 80 · 443 hostPort를 놓기 전에는 스케줄되지 않습니다. 그래서 Traefik을 먼저 옮깁니다. NodePort 30200, nginx 매니페스트, certbot CronJob은 이 단계에서 지우지 않았으므로 그대로 이어받습니다.

```bash
cd ~/kidly-traefik/deploy/traefik
export KUBECONFIG=$HOME/.kube/config
MONITORING_HOST=<모니터링 서버 사설 IP> WEB_PORT=18080 WEBSECURE_PORT=18443 ./deploy.sh <도메인>
kubectl -n edge scale deploy/nginx --replicas 1
kubectl -n edge wait --for=condition=Ready pod -l app=nginx --timeout 120s
curl -s -o /dev/null -w '%{http_code}\n' "https://api.<도메인>/api/v1/products?page=0&size=1"   # 200
```

## 2단계: 80 · 443 넘기기

전환 시각은 #1349 Agent 패치(Gateway · 주문 Deployment 수동 패치) 적용과 겹치지 않게 잡습니다. 전환 전에 `kubectl -n kidly-dev get deploy gateway order` 로 진행 중인 롤아웃이 없는지 봅니다. Traefik이 추적 헤더를 지우므로 전환 뒤에는 Agent를 붙여도 됩니다.

nginx를 내린 뒤 Traefik을 80 · 443으로 배포합니다. 두 Pod가 같은 호스트 포트를 선언하므로 순서를 바꿀 수 없고, 그 사이 공개 요청이 끊깁니다. 2026-11-16 전환에서 약 17초 끊겼습니다(바깥에서 1초마다 호출해 잰 값). Traefik Pod가 Recreate로 옛 Pod를 내린 뒤 뜨는 시간입니다.

```bash
cd ~/kidly-traefik/deploy/traefik
export KUBECONFIG=$HOME/.kube/config
kubectl -n edge scale deploy/nginx --replicas 0
MONITORING_HOST=<모니터링 서버 사설 IP> ./deploy.sh <도메인>
```

바깥 PC에서 확인합니다.

```bash
DOMAIN=<도메인>
curl -s -o /dev/null -w '%{http_code} %{http_version}\n' "https://api.$DOMAIN/api/v1/products?page=0&size=1"   # 200 2
curl -s "https://api.$DOMAIN/actuator/prometheus"                                    # 404 ProblemDetail
curl -s -o /dev/null -w '%{http_code}\n' "https://grafana.$DOMAIN/api/health"        # 200
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' "http://api.$DOMAIN/"       # 301 https://api.<도메인>/
openssl s_client -connect "api.$DOMAIN:443" -servername "unknown.$DOMAIN" </dev/null # 핸드셰이크 실패
curl -sk "https://<탄력적 IP>/"                                                       # 핸드셰이크 실패(SNI 없음)
```

헤더는 1단계 5번의 whoami를 443으로 붙여 확인합니다(`--resolve` 없이 `https://api.$DOMAIN/__whoami`). `X-Real-Ip`가 그 PC의 공인 IP여야 합니다.

certbot CronJob은 그대로 둡니다. 갱신은 만료 30일 전(2027-01-15)부터 시작해 그 전에는 아무것도 하지 않습니다. nginx가 없어 webroot 갱신은 실패하므로 그 전에 3단계(Traefik ACME)를 마칩니다.

3단계 ACME는 다음 단계에서 되돌리기와 함께 이 README에 추가합니다. 3단계에서는 `tls.secretName`을 `tls.certResolver`로 바꿉니다.
