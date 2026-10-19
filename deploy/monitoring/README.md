# 모니터링 (Prometheus · Loki · Grafana)

EC2 안에서 앱의 메트릭과 로그를 모으고 Grafana 로 봅니다. 앱 다운 · 5xx 증가 · ERROR 로그는 Slack `#4팀-운영이슈` 로 알립니다.
모두 host 네트워크에서 `127.0.0.1` 에만 열립니다. 밖에서는 nginx 의 `https://grafana.<도메인>` 으로 Grafana 만 보입니다.

| 이름 | 주소 (서버 안) | 하는 일 | 보관 |
| --- | --- | --- | --- |
| Prometheus | `127.0.0.1:9090` | 앱 `/actuator/prometheus` 를 15초마다 수집 (지금은 dev 만) | 15일 |
| Loki | `127.0.0.1:3100` | 앱 컨테이너 로그 저장 | 14일 |
| Alloy | `127.0.0.1:12345` | docker 에서 dev · prod 앱 로그를 읽어 Loki 로 보냄 | - |
| Grafana | `127.0.0.1:3000` | 대시보드 · 로그 검색 · 알림 | - |

데이터소스 · 대시보드 · 알림 규칙 · Slack 연결은 `grafana/` 아래 파일로 관리합니다.
JVM 대시보드(`grafana/dashboards/jvm-micrometer.json`)는 커뮤니티 대시보드 [4701](https://grafana.com/grafana/dashboards/4701) 리비전 10 을 받아
데이터소스를 uid `prometheus` 로 고정하고, 이 앱에 없는 `application` 라벨 조건을 `job="kidly"` 로 바꾼 것입니다.

## 준비

1. `deploy/nginx` 를 설치해 `grafana.<도메인>` 주소와 인증서를 만듭니다. DNS 에 `grafana` A 레코드가 있어야 합니다.
2. Slack `#4팀-운영이슈` 의 Incoming Webhook URL 을 받습니다.
3. 서버에 docker 와 compose 플러그인이 설치되어 있어야 합니다.

## 설치

로컬에서 폴더를 서버로 복사합니다. 서버에 옛 폴더가 있으면 지운 파일이 남지 않도록 먼저 지웁니다.

```bash
ssh ec2-user@<탄력적 IP> 'rm -rf ~/monitoring'
scp -r deploy/monitoring ec2-user@<탄력적 IP>:~/
```

서버에 접속해 실행합니다. 처음에는 `/opt/monitoring/.env` 만 만들고 멈춥니다.

```bash
sudo ~/monitoring/install.sh
sudo vi /opt/monitoring/.env    # GRAFANA_ADMIN_PASSWORD, SLACK_OPS_WEBHOOK_URL 을 채운다
sudo ~/monitoring/install.sh
```

값에 `$` 가 있으면 `GRAFANA_ADMIN_PASSWORD='값'` 처럼 작은따옴표로 감쌉니다. 감싸지 않으면 compose 가 변수로 읽습니다.
`GRAFANA_ADMIN_PASSWORD` 는 Grafana 를 처음 만들 때만 쓰입니다. 그 뒤에 바꾸려면 Grafana 화면에서 바꿉니다.

## 확인

```bash
curl -s 127.0.0.1:9090/api/v1/targets | grep -o '"health":"[a-z]*"'   # "health":"up"
curl -s 127.0.0.1:3100/ready                                           # ready
curl -s 127.0.0.1:3000/api/health
```

`https://grafana.<도메인>` 에 `admin` 으로 로그인해 Kidly 폴더의 대시보드를 봅니다.
알림 → 연락처 지점 → `slack-ops` 의 "Test" 로 Slack 수신을 확인합니다.

#453 이전 커밋(롤백 · 오래된 기능 브랜치)을 dev 에 올리면 `/actuator/prometheus` 가 없어 "앱 다운" 이 울립니다. 그동안은 Grafana 에서 그 규칙을 일시정지합니다.

## 설정 바꾸기

레포의 파일을 고친 뒤 다시 복사하고 install.sh 를 실행합니다. `.env` 와 데이터(볼륨)는 그대로 남습니다.

```bash
ssh ec2-user@<탄력적 IP> 'rm -rf ~/monitoring'
scp -r deploy/monitoring ec2-user@<탄력적 IP>:~/
sudo ~/monitoring/install.sh
```

Grafana 화면에서 고친 대시보드 · 알림 규칙은 저장되지 않거나 다음 설치 때 파일 내용으로 돌아갑니다. 바꿀 것은 레포에서 고칩니다.

## prod 추가

prod 앱이 처음 배포되면 `prometheus/prometheus.yml` 의 주석으로 둔 `127.0.0.1:8080` 대상을 풀고 다시 설치합니다.
없는 대상을 미리 넣으면 "앱 다운" 알림이 계속 울립니다. 로그는 Alloy 가 prod 앱 컨테이너를 알아서 찾습니다.

## 로그 보기

Grafana → Explore → 데이터소스 Loki 에서 검색합니다. 스택 트레이스는 로그 한 건으로 묶여 있습니다.

```
{env="dev", level="ERROR"}
{env="dev"} |= "traceId 값"
```
