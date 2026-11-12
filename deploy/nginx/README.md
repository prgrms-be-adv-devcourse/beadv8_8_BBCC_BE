# nginx + HTTPS 설정

앱 서버(t3.large)의 단독 k3s(namespace `edge`)에서 nginx Pod 가 80 · 443 을 받아 Gateway(같은 서버 k3s 의 NodePort `127.0.0.1:30200`, deploy/k3s)와 Grafana(모니터링 서버 t3.medium 의 Traefik `<사설 IP>:80`)로 넘깁니다(#1309). 예전에는 호스트에 직접 설치한 nginx 였고, 설정(`kidly.conf.template`)은 그대로 옮겼습니다.
`/actuator` 와 그 아래 경로는 `/actuator/health` 만 밖으로 열고 나머지는 404 입니다. 밖에서 온 `X-Member-*` · `X-Admin-*` · `X-Child-*` 헤더는 지웁니다.

| 주소 | 연결되는 곳 |
| --- | --- |
| `https://dev-api.<도메인>` | Gateway (k3s NodePort 30200). `/actuator/health` 도 Gateway가 받음 |
| `https://grafana.<도메인>` | Grafana (t3.medium k3s, `deploy/monitoring`) |
| `https://api.<도메인>`, IP 로 직접 접속, 모르는 호스트 | 응답 없이 끊음. prod 는 걷어냈다(#1213). `api` 는 인증서 갱신 확인용으로 80 에만 남는다 |

## 구성 (`nginx.yaml`)

| 무엇 | 내용 |
| --- | --- |
| Deployment `nginx` | `nginx:1.28.0`, 호스트 네트워크(80 · 443). 설정은 ConfigMap `nginx-conf-<해시>`. 인증서는 호스트 `/etc/letsencrypt` 를 읽기 전용으로 읽는다. 6시간마다 `nginx -s reload` 로 갱신된 인증서를 읽는다 |
| CronJob `certbot-renew` | `certbot/certbot:v5.8.0` 으로 매일 10:30 KST `certbot renew`. 호스트 `/etc/letsencrypt` · `/var/lib/letsencrypt` · `/var/www/certbot` 를 쓴다(예전 docker certbot 과 같은 마운트). 서버가 꺼져 그 시간을 놓치면 켜진 뒤 돈다 |
| 메모리 request | nginx 32Mi, certbot Job 64Mi(하루 한 번 잠깐). nginx 는 PriorityClass `kidly-edge` 라 노드 request 가 꽉 차면 다른 Pod 를 밀어내고 뜬다 |

인증서는 ZeroSSL 에서 받았습니다(ACME). `kro.kr` 은 공용 접미사 목록(PSL)에 없어 Let's Encrypt 의 "등록 도메인당 주 50장" 한도를 모든 `kro.kr` 사용자와 나눠 쓰기 때문에 자주 막힙니다.
ZeroSSL 계정은 `/etc/letsencrypt/accounts` 에 있어 갱신에 EAB 키가 필요 없습니다. 인증서는 90일짜리이고 만료 30일 전부터 실제로 새로 받습니다.

## 배포 · 설정 바꾸기

nginx 는 앱 배포로 바뀌지 않습니다. `kidly.conf.template` · `nginx.yaml` 을 고친 뒤 이 폴더를 서버에 올려 `deploy.sh` 를 실행합니다.
deploy 유저(kubectl 이 되는 계정)로 실행하고, sudo 는 필요 없습니다.

```bash
# 내 PC 에서
git archive origin/develop deploy/nginx | ssh <deploy 유저>@<탄력적 IP> 'rm -rf ~/kidly-nginx && mkdir ~/kidly-nginx && tar -x -C ~/kidly-nginx'
# t3.large 에서
cd ~/kidly-nginx/deploy/nginx && MONITORING_HOST=<모니터링 서버 사설 IP> ./deploy.sh kidly.kro.kr
```

`deploy.sh` 는 설정을 ConfigMap 으로 만들어 `nginx -t` 로 먼저 검사합니다. 통과하지 못하면 떠 있는 nginx 를 건드리지 않고 멈춥니다.
통과하면 Pod 를 바꿉니다. 호스트 포트를 쓰므로 옛 Pod 를 내린 뒤 새 Pod 가 떠서 몇 초 끊깁니다.
`MONITORING_HOST` 는 Grafana 가 도는 t3.medium 의 사설 IP 입니다. 그 서버의 보안 그룹이 이 서버에서 오는 80 을 받아야 합니다.

## 호스트 nginx 에서 옮기기 (처음 한 번)

1. 위처럼 `deploy.sh` 를 실행합니다. 호스트 nginx 가 80 · 443 을 쥐고 있으면 Pod 는 놓을 때까지 기다리고, 스크립트는 다음 명령을 알려 주고 끝납니다.
2. 호스트 nginx 와 예전 갱신 타이머를 끕니다. Pod 가 1초 안에 이어받습니다.
   ```bash
   sudo systemctl disable --now nginx kidly-certbot-renew.timer
   kubectl -n edge rollout status deploy/nginx
   curl -I https://dev-api.kidly.kro.kr/actuator/health
   ```
3. 갱신을 한 번 시험합니다(실제 인증서는 바뀌지 않음).
   ```bash
   kubectl -n edge create job certbot-check --from=cronjob/certbot-renew --dry-run=client -o yaml \
     | sed 's/^\( *\)- --no-random-sleep-on-renew$/&\n\1- --dry-run/' | kubectl apply -f -
   kubectl -n edge wait --for=condition=complete job/certbot-check --timeout 300s && kubectl -n edge logs job/certbot-check | tail -5
   kubectl -n edge delete job certbot-check
   ```
4. 하루 지켜본 뒤 호스트 패키지와 예전 unit 을 지웁니다.
   ```bash
   sudo rm -f /etc/systemd/system/kidly-certbot-renew.{service,timer} && sudo systemctl daemon-reload
   sudo dnf remove -y nginx && sudo rm -rf /opt/certbot
   ```

되돌리기: `kubectl -n edge scale deploy/nginx --replicas 0 && sudo systemctl enable --now nginx kidly-certbot-renew.timer`

## 새 서버 (인증서가 없을 때)

DNS 에 `api` · `dev-api` · `grafana` A 레코드(탄력적 IP)를 만들고, 보안 그룹에서 80 · 443 을 엽니다.
인증서가 없으면 nginx 설정 검사가 통과하지 않으므로, 먼저 인증서를 받습니다. 80 이 비어 있어야 합니다(certbot 이 직접 받는 standalone 방식).
ZeroSSL 계정이 없으므로 EAB 키가 필요합니다. ZeroSSL 대시보드 > Developer 에서 만듭니다.

```bash
sudo k3s kubectl -n edge run certbot-issue --rm -i --restart=Never --image certbot/certbot:v5.8.0 \
  --overrides='{"spec":{"hostNetwork":true,"volumes":[{"name":"le","hostPath":{"path":"/etc/letsencrypt"}}],"containers":[{"name":"certbot-issue","image":"certbot/certbot:v5.8.0","stdin":true,"volumeMounts":[{"name":"le","mountPath":"/etc/letsencrypt"}],"args":["certonly","--standalone","--server","https://acme.zerossl.com/v2/DV90","--eab-kid","<kid>","--eab-hmac-key","<hmac>","-d","api.<도메인>","-d","dev-api.<도메인>","-d","grafana.<도메인>","--cert-name","kidly","--email","<이메일>","--agree-tos","--no-eff-email","--non-interactive"]}]}}'
```

받은 뒤 갱신이 webroot 로 돌도록 `/etc/letsencrypt/renewal/kidly.conf` 의 `authenticator` 를 `webroot`, `webroot_path` 를 `/var/www/certbot,` 으로 바꾸고 `deploy.sh` 를 실행합니다.
`--agree-tos` 로 [ZeroSSL 약관](https://zerossl.com/terms/)에 동의하게 됩니다. 실행 전에 한 번 읽어 보세요.

## 확인

```bash
curl -I https://dev-api.<도메인>/actuator/health
curl -I https://grafana.<도메인>/api/health
kubectl -n edge get pods,cronjob                  # nginx Running, certbot-renew 의 마지막 실행 시각
kubectl -n edge logs job/<certbot-renew-…>        # 갱신 기록
```
