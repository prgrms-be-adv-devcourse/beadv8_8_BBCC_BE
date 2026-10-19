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

**이 기능을 처음 develop 에 머지하기 전에** 1 · 2 를 끝냅니다. 머지하면 앱이 바로 dev 에 배포되는데, nginx 가 옛 설정이면 그 사이 `/actuator/prometheus` 가 밖에 열립니다.

1. `deploy/nginx` 를 설치(이미 있으면 다시 실행)해 `/actuator` 를 막고 `grafana.<도메인>` 주소와 인증서를 만듭니다. DNS 에 `grafana` A 레코드가 있어야 합니다.
2. Slack `#4팀-운영이슈` 의 Incoming Webhook URL 을 받습니다.
3. 서버에 docker 와 compose 플러그인이 설치되어 있어야 합니다.

## 설치

`.github/workflows/monitoring-deploy.yml` 이 이 폴더를 서버 `/opt/app/monitoring` 에 올리고 `deploy.sh` 로 컨테이너를 다시 만듭니다.
서버 `.env` 는 Environment `dev` 의 Secret 으로 배포마다 새로 만듭니다. 데이터(docker 볼륨)는 그대로 남습니다.

### 처음 한 번

Secret 두 개를 등록합니다. 값을 물어보면 붙여 넣습니다(명령줄 · 화면에 남지 않습니다).

```bash
gh secret set SLACK_OPS_WEBHOOK_URL --env dev
gh secret set GRAFANA_ADMIN_PASSWORD --env dev
```

값에 작은따옴표 · 줄바꿈은 쓸 수 없습니다(워크플로가 멈춥니다). `$` 등 다른 문자는 그대로 쓰입니다.

### 이후

`develop` 에 `deploy/monitoring/**` 변경이 머지되면 자동으로 배포됩니다.
손으로 돌리려면 GitHub Actions → monitoring-deploy → Run workflow 를 누릅니다(`gh workflow run monitoring-deploy.yml --ref develop`).

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

레포의 파일을 고쳐 `develop` 에 머지하면 워크플로가 다시 배포합니다. 레포에서 지운 설정 파일은 서버에서도 지워집니다.

비밀을 바꿀 때는 Secret 을 고친 뒤(`gh secret set ... --env dev`) 워크플로를 손으로 실행합니다.
**Grafana 관리자 비밀번호는 Secret 이 원본입니다. Grafana 화면에서 바꾸지 마세요.** 배포마다 Secret 값으로 맞춰지므로 화면에서 바꾼 비밀번호는 다음 배포 때 되돌아갑니다.

Grafana 화면에서 고친 대시보드 · 알림 규칙은 저장되지 않거나 다음 배포 때 파일 내용으로 돌아갑니다. 바꿀 것은 레포에서 고칩니다.

## prod 추가

prod 앱이 처음 배포되면 `prometheus/prometheus.yml` 의 주석으로 둔 `127.0.0.1:8080` 대상을 풀어 `develop` 에 머지합니다.
없는 대상을 미리 넣으면 "앱 다운" 알림이 계속 울립니다. 로그는 Alloy 가 prod 앱 컨테이너를 알아서 찾습니다.

## 로그 보기

Grafana → Explore → 데이터소스 Loki 에서 검색합니다. 스택 트레이스는 로그 한 건으로 묶여 있습니다.

```
{env="dev", level="ERROR"}
{env="dev"} |= "traceId 값"
```

## 알려진 한계

- **모니터링 자체가 죽으면 알림도 멈춥니다.** Grafana 가 알림을 보내므로 Grafana · Prometheus 가 죽거나 EC2 가 꺼지면 알릴 수 없습니다. 감지되는 것은 `monitoring-deploy` 실패 알림(`#4팀-알림`)뿐입니다.
- **앱이 막 재시작한 직후의 ERROR 는 건수 알림에서 빠질 수 있습니다.** 첫 수집(15초) 전에 난 ERROR 는 카운터에 잡히지 않습니다. 로그(Loki)에는 남습니다.
- **Alloy 는 로그를 읽으려고 `/var/run/docker.sock` 을 마운트합니다.** `:ro` 여도 Docker API 는 막지 못해, Alloy 가 침해되면 서버 root 와 같은 권한이 됩니다. 127.0.0.1 에만 열고 원격 설정을 쓰지 않는 것으로 위험을 줄였습니다. 더 줄이려면 `docker-socket-proxy` 로 `containers` · `events` 만 열 수 있습니다.
