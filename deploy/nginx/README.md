# nginx + HTTPS 설정

EC2 한 대에서 nginx 가 443 을 받아 운영 앱(`127.0.0.1:8080`)과 개발 앱(`127.0.0.1:8081`)으로 넘깁니다.
인증서는 Let's Encrypt 에서 무료로 받고, certbot 도커 이미지로 발급·갱신합니다.

| 주소 | 연결되는 곳 |
| --- | --- |
| `https://api.<도메인>` | 운영 앱 (8080) |
| `https://dev-api.<도메인>` | 개발 앱 (8081) |
| IP 로 직접 접속, 모르는 호스트 | 응답 없이 끊음 |

## 준비

1. 도메인을 삽니다.
2. DNS 에 A 레코드 두 개를 만듭니다. 값은 둘 다 EC2 의 탄력적 IP 입니다.
   - `api` → 탄력적 IP
   - `dev-api` → 탄력적 IP
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
sudo ./install.sh <도메인> <이메일>
# 예) sudo ./install.sh kidly.example team@kidly.example
```

여러 번 실행해도 됩니다. 인증서가 이미 있으면 발급은 건너뜁니다.

`--agree-tos` 로 [Let's Encrypt 가입자 약관](https://letsencrypt.org/repository/)에 동의하게 됩니다.
실행 전에 한 번 읽어 보세요.

## 확인

```bash
curl -I https://dev-api.<도메인>/actuator/health
curl -I https://api.<도메인>/actuator/health
```

## 자동 갱신

인증서는 90일짜리입니다. 매일 10:30 KST 에 갱신을 시도하고, 만료 30일 전부터 실제로 새로 받습니다.
서버가 꺼져 있어 그 시간을 놓치면 켜진 뒤 바로 돕니다.

```bash
systemctl list-timers kidly-certbot-renew.timer   # 다음 실행 시각
journalctl -u kidly-certbot-renew.service         # 갱신 기록
```
