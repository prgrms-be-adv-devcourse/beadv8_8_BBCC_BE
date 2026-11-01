# nginx + HTTPS 설정

앱 서버(t3.large)에서 nginx 가 443 을 받아 운영 Gateway(`127.0.0.1:8084`, 운영 앱 `127.0.0.1:8080` 앞), 개발 Gateway(`127.0.0.1:8083`, 개발 앱 `127.0.0.1:8081` 앞), Grafana(모니터링 서버 t3.medium 의 Traefik `<사설 IP>:80`)로 넘깁니다.
`/actuator` 와 그 아래 경로는 `/actuator/health` 만 밖으로 열고 나머지는 404 입니다.
인증서는 ZeroSSL 에서 무료로 받고(ACME), certbot 도커 이미지로 발급·갱신합니다.
`kro.kr` 은 공용 접미사 목록(PSL)에 없어 Let's Encrypt 의 "등록 도메인당 주 50장" 한도를 모든 `kro.kr` 사용자와 나눠 쓰기 때문에 자주 막힙니다.

| 주소 | 연결되는 곳 |
| --- | --- |
| `https://api.<도메인>` | Gateway (8084) → 운영 앱 (8080). `/actuator/health` 는 운영 앱이 바로 받음 |
| `https://dev-api.<도메인>` | Gateway (8083) → 개발 앱 (8081). `/actuator/health` 는 개발 앱이 바로 받음 |
| `https://grafana.<도메인>` | Grafana (t3.medium k3s, `deploy/monitoring`) |
| IP 로 직접 접속, 모르는 호스트 | 응답 없이 끊음 |

## 준비

1. 도메인을 삽니다.
2. DNS 에 A 레코드 세 개를 만듭니다. 값은 모두 EC2 의 탄력적 IP 입니다.
   - `api` → 탄력적 IP
   - `dev-api` → 탄력적 IP
   - `grafana` → 탄력적 IP
   - `dig +short api.<도메인>` 이 탄력적 IP 를 돌려주면 준비된 것입니다.
3. 보안 그룹에서 80, 443 이 열려 있어야 합니다. 80 은 인증서 발급·갱신에 씁니다.
4. 서버에 docker 가 설치되어 있어야 합니다.

## 설치

로컬에서 폴더를 서버로 복사합니다.

```bash
scp -r deploy/nginx ec2-user@<탄력적 IP>:~/
```

서버에 접속해 실행합니다. 이메일은 만료 안내를 받을 주소입니다.

```bash
cd ~/nginx
sudo MONITORING_HOST=<모니터링 서버 사설 IP> ./install.sh <도메인> <이메일>
# 예) sudo MONITORING_HOST=10.0.1.23 ./install.sh kidly.example team@kidly.example
```

`MONITORING_HOST` 는 Grafana 가 도는 t3.medium 의 사설 IP 입니다(필수). 그 서버의 보안 그룹이 이 서버에서 오는 80 을 받아야 합니다.

여러 번 실행해도 됩니다. 인증서 이름이 그대로면 발급은 건너뜁니다.
이미 설치한 서버에서 이름을 추가할 때도 install.sh 를 다시 실행하면 됩니다.
nginx 설정은 앱 배포로 바뀌지 않습니다. 템플릿을 고친 뒤에는 새 `deploy/nginx` 를 서버에 다시 올리고 install.sh 를 다시 실행해야 반영됩니다.

Gateway(dev #608, prod #638)를 켤 때는 순서를 지킵니다. 환경별로 따로 합니다(dev: `.env.dev` · `dev-gateway`, prod: `.env.prod` · `prod-gateway`).

- 처음 켤 때: nginx 를 먼저 Gateway 로 돌리고(install.sh) 그다음 `.env.<환경>` 의 `COMPOSE_PROFILES` 에 `gateway` 가 든 배포를 합니다. #638 뒤로 서비스는 `X-Member-*` 헤더를 그대로 믿으므로, 배포가 먼저면 nginx 가 앱으로 바로 넘기는 사이 누구나 헤더를 위조할 수 있습니다. 대신 배포가 끝나 gateway 컨테이너가 healthy 가 될 때까지(`docker ps --filter name=<환경>-gateway`) 그 환경은 502 입니다. prod 는 아직 운영 전이라 502 가 나도 피해가 없습니다
- 끄지 않습니다: Gateway 를 끄는 것은 롤백 수단이 아닙니다(#638 뒤로 서비스는 `X-Member-*` 헤더를 그대로 믿습니다). 템플릿의 `location /` 를 앱 포트(dev 8081, prod 8080)로 되돌리면 Gateway 를 거치지 않으므로 누구나 헤더를 위조해 다른 회원 · 관리자로 요청할 수 있습니다

새 서버처럼 ZeroSSL 계정이 없으면 EAB 키가 필요합니다. ZeroSSL 대시보드 > Developer 에서 만들어 환경 변수로 넘깁니다(처음 한 번).

```bash
sudo ZEROSSL_EAB_KID=<kid> ZEROSSL_EAB_HMAC_KEY=<hmac> MONITORING_HOST=<모니터링 서버 사설 IP> ./install.sh <도메인> <이메일>
```

`--agree-tos` 로 [ZeroSSL 약관](https://zerossl.com/terms/)에 동의하게 됩니다.
실행 전에 한 번 읽어 보세요.

## 확인

```bash
curl -I https://dev-api.<도메인>/actuator/health
curl -I https://api.<도메인>/actuator/health
curl -I https://grafana.<도메인>/api/health
```

## 자동 갱신

인증서는 90일짜리입니다. 매일 10:30 KST 에 갱신을 시도하고, 만료 30일 전부터 실제로 새로 받습니다.
서버가 꺼져 있어 그 시간을 놓치면 켜진 뒤 바로 돕니다.

```bash
systemctl list-timers kidly-certbot-renew.timer   # 다음 실행 시각
journalctl -u kidly-certbot-renew.service         # 갱신 기록
```
