# Traefik 엣지

t3.large 의 k3s 내장 Traefik(`kube-system/traefik`)이 80 · 443 을 받아 Gateway 와 Grafana 로 넘깁니다(#1423, #1599). 라우트 · 미들웨어는 namespace `edge` 에 둡니다.

| 역할 | 설정 |
| --- | --- |
| TLS 종료 | TLSOption `default`(`sniStrict`, TLS 1.2 이상), 인증서는 Traefik ACME(ZeroSSL) |
| Host 라우팅 | IngressRoute. api 는 Gateway(`kidly-dev/gateway`), grafana 는 모니터링 서버(셀렉터 없는 Service + EndpointSlice) |
| 클라이언트 IP | entrypoint 의 forwardedHeaders 신뢰 목록을 비워 들어온 `X-Forwarded-*` · `X-Real-IP` 를 덮어쓴다. hostPort(portmap DNAT)가 바깥 출발지 IP 를 그대로 둔다 |
| HTTP→HTTPS | `http-redirect` 라우터. ACME HTTP-01 경로는 Traefik 이 먼저 받는다 |
| 추적 헤더 제거 | `strip-trace-headers` 미들웨어(`traceparent` · `tracestate` · `baggage`) |
| 요청 본문 10MB | `limit-request-body`(Buffering, 넘으면 413). SSE 라우트(`Accept: text/event-stream` 또는 `…/stream`)에는 걸지 않는다. v3.7 Buffering 은 응답도 모아 보내 SSE 가 끊긴 것처럼 보인다 |

모르는 SNI 와 IP 직접 접속(SNI 없음)은 `sniStrict` 가 핸드셰이크를 거부합니다. HTTP 의 모르는 Host 는 Traefik 404 입니다. Nginx 의 444 와 다르지만 앱에는 닿지 않습니다.

k3s 를 `--disable traefik` 없이 실행하고, `traefik.yaml` 의 HelmChartConfig 로 내장 Traefik 에 값만 덧씌웁니다. 차트와 이미지는 k3s 가 정하므로 k3s 를 올리면 Traefik 도 함께 올라갑니다. 그때는 아래 「확인」을 다시 돌립니다. hostNetwork 는 1024 아래 포트에 root 가 필요하고 ServiceLB 는 꺼 두어 hostPort 를 씁니다. 메모리 request 64Mi(실측 약 25Mi), 우선순위는 k3s 기본값 `system-cluster-critical` 입니다.

앱과 따로 배포합니다. 엣지는 드물게 바뀌고 앱 배포 실패가 엣지에 번지지 않게 합니다.

## 인증서

ZeroSSL 에서 Traefik ACME(resolver `zerossl`, HTTP-01, EC256)로 받습니다. `kro.kr` 은 공용 접미사 목록(PSL)에 없어 Let's Encrypt 의 "등록 도메인당 주 50장" 한도를 모든 `kro.kr` 사용자와 나눠 쓰므로 ZeroSSL 을 씁니다. api · grafana 는 IngressRoute 의 Host 마다 따로 받습니다.

| 무엇 | 값 |
| --- | --- |
| 지금 인증서 | api · grafana 각각, 2026-11-16 발급, 2027-02-14 만료(ZeroSSL ECC DV SSL CA 2). 2027-01-15 무렵부터 갱신된다 |
| 갱신 | 90일 인증서를 만료 30일 전부터 Traefik 이 새로 받는다(Traefik 문서). Traefik 이 뜰 때도 검사하므로(로그 `Testing certificate renew...`) 서버가 밤에 꺼져도 된다. 80 이 밖에서 열려 있어야 한다 |
| `acme.json` | 호스트 `/var/lib/kidly/traefik/acme.json`(Pod 의 `/acme`). ACME 계정 키와 인증서 개인 키가 들어 있어 비밀로 다룬다. 디렉터리는 uid 65532(Traefik), 700 |
| EAB 키 | Secret `kube-system/zerossl-eab`(키 `ZEROSSL_EAB_KID` · `ZEROSSL_EAB_HMAC_KEY`). 원본은 호스트 `/etc/kidly/zerossl-eab.env`(root, 600). Traefik 인자에는 `$(ZEROSSL_EAB_KID)` 자리만 있고 kubelet 이 펼친다. 그래서 Deployment · 로그에는 값이 없고, 노드의 Traefik 프로세스 인자(`/proc/<pid>/cmdline`, root 만)에만 있다 |

백업 · 복구(ec2-user):

```bash
sudo cp -p /var/lib/kidly/traefik/acme.json ~/acme.json.bak && sudo chmod 600 ~/acme.json.bak   # 백업
sudo install -m 600 -o 65532 -g 65532 ~/acme.json.bak /var/lib/kidly/traefik/acme.json         # 복구
sudo k3s kubectl -n kube-system rollout restart deploy/traefik                                  # 다시 읽기(약 17초 끊김)
```

`acme.json` 을 잃으면 Traefik 이 뜰 때 다시 받습니다. 받을 때까지 api · grafana 핸드셰이크가 거부됩니다.

## 처음 설치 (새 서버)

1. DNS 에 `api` · `grafana` A 레코드(탄력적 IP)를 만들고, 보안 그룹에서 80 · 443 을 엽니다. 모니터링 서버 보안 그룹은 이 서버에서 오는 80 을 받아야 합니다.
2. EAB 키를 만듭니다(ec2-user). 키 값은 출력하지 않습니다.

   ```bash
   sudo install -d -m 700 -o root -g root /etc/kidly
   curl -s -X POST https://api.zerossl.com/acme/eab-credentials-email \
     --data "email=programmers.bbcc.official@gmail.com" \
     | sudo python3 -c 'import json,os,sys; d=json.load(sys.stdin); assert d.get("success"), {k:v for k,v in d.items() if not k.startswith("eab_")}; fd=os.open("/etc/kidly/zerossl-eab.env", os.O_WRONLY|os.O_CREAT|os.O_EXCL, 0o600); os.write(fd, ("ZEROSSL_EAB_KID=%s\nZEROSSL_EAB_HMAC_KEY=%s\n" % (d["eab_kid"], d["eab_hmac_key"])).encode())'
   ```

3. k3s 를 `--disable servicelb --disable local-storage` 로 설치합니다. `--disable traefik` 을 붙이면 `deploy.sh` 가 멈춥니다. 그다음 `acme.json` 디렉터리와 EAB Secret 을 만듭니다(ec2-user).

   ```bash
   sudo install -d -m 755 /var/lib/kidly && sudo install -d -m 700 -o 65532 -g 65532 /var/lib/kidly/traefik
   sudo k3s kubectl -n kube-system create secret generic zerossl-eab --from-env-file=/etc/kidly/zerossl-eab.env
   ```

4. 아래 「바꾸기 · 배포」대로 올립니다. 처음 뜨면 인증서를 받는 동안 api · grafana 핸드셰이크가 거부됩니다.

## 바꾸기 · 배포

```bash
# 내 PC 에서
git archive origin/develop deploy/traefik | ssh <deploy 유저>@<탄력적 IP> 'rm -rf ~/kidly-traefik && mkdir ~/kidly-traefik && tar -x -C ~/kidly-traefik'
# t3.large 에서 deploy 유저로
cd ~/kidly-traefik/deploy/traefik && MONITORING_HOST=<모니터링 서버 사설 IP> ./deploy.sh <도메인>
```

`traefik.yaml` 이 바뀌면 helm upgrade 로 Pod 가 바뀝니다. hostPort 라 옛 Pod 를 내린 뒤 새 Pod 가 떠서(Recreate) 약 17초 끊깁니다. `routes.yaml` 만 바뀌면 Pod 는 그대로이고, `deploy.sh` 가 끝난 뒤 몇 초 지나 반영됩니다.
deploy 유저로 kubectl 을 직접 쓸 때는 먼저 `export KUBECONFIG=$HOME/.kube/config` 를 실행합니다(`deploy.sh` 는 스스로 넣는다).

## 확인

바깥 PC 에서:

```bash
DOMAIN=<도메인>
curl -s -o /dev/null -w '%{http_code} %{http_version}\n' "https://api.$DOMAIN/api/v1/products?page=0&size=1"   # 200 2
curl -s "https://api.$DOMAIN/actuator/prometheus"                                    # 404 ProblemDetail
curl -s -o /dev/null -w '%{http_code}\n' "https://grafana.$DOMAIN/api/health"        # 200
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' "http://api.$DOMAIN/"       # 301 https://api.<도메인>/
curl -s -o /dev/null -w '%{http_code}\n' "http://api.$DOMAIN/.well-known/acme-challenge/x"   # 404(리다이렉트되지 않음)
openssl s_client -connect "api.$DOMAIN:443" -servername "unknown.$DOMAIN" </dev/null  # 핸드셰이크 실패
echo | openssl s_client -connect "api.$DOMAIN:443" -servername "api.$DOMAIN" 2>/dev/null | openssl x509 -noout -issuer -enddate
```

헤더는 임시 whoami 로 봅니다. 위조한 IP 대신 그 PC 의 공인 IP 가 오고 추적 헤더가 없어야 합니다. 확인 뒤 지웁니다.

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
  tls: {certResolver: zerossl}
EOF
# 바깥 PC 에서
curl -H 'X-Real-IP: 203.0.113.10' -H 'X-Forwarded-For: 203.0.113.10' -H 'traceparent: 00-0123456789abcdef0123456789abcdef-0123456789abcdef-01' "https://api.$DOMAIN/__whoami"
# 서버에서
kubectl -n edge delete ingressroute,service,deployment whoami
```

## 되돌리기

이전 커밋의 `deploy/traefik`을 같은 방법으로 다시 배포합니다. `acme.json`은 그대로 씁니다.

k3s 에 `--disable traefik` 을 다시 붙이면 k3s 가 내장 Traefik 과 함께 Traefik CRD 를 지워 `edge` 의 라우트 · 미들웨어도 사라집니다. 그때는 `deploy.sh` 로 라우트를 다시 올려야 응답합니다.

## Nginx 에서 옮긴 기록 (2026-11-16)

1. Traefik 을 테스트 포트(hostPort 18080 · 18443)로 띄워 Nginx 와 함께 검증했습니다. 인증서는 certbot 것을 `kidly-tls` 로 썼습니다.
2. 07:37 UTC 에 Nginx 를 0개로 줄이고 Traefik 을 80 · 443 으로 배포했습니다. 바깥에서 1초마다 호출해 약 17초 끊겼습니다. 전환 시각에는 #1349 Agent 패치가 아직 적용되지 않았습니다.
3. ACME 로 바꾼 배포(07:54 UTC)에서 Pod 교체로 약 11초 끊겼습니다. 발급 전까지 `sniStrict` 가 핸드셰이크를 거부하지 않게 기본 TLSStore 에 `kidly-tls` 를 넣어 두었지만, Traefik ACME 는 저장소에 이미 인증서가 있는 도메인은 받지 않아 발급이 시작되지 않았습니다. TLSStore 를 지우자 22초 만에 두 인증서를 받았고 그동안 약 20초 끊겼습니다. 그 뒤 certbot CronJob 을 지웠습니다. 당시 `/etc/letsencrypt` 와 Secret `kidly-tls` 는 되돌리기용으로 남겼습니다.
4. 2026-11-16 정리에서 NodePort 30200, `deploy/nginx`, Secret `kidly-tls`, `/etc/letsencrypt`, 호스트 nginx 패키지를 삭제했습니다.
