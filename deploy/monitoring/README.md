# 모니터링 (Prometheus · Loki · Grafana)

EC2 안에서 앱의 메트릭과 로그를 모으고 Grafana 로 봅니다. 앱 다운 · 5xx 증가 · ERROR 로그는 Slack `#4팀-운영이슈` 로 알립니다.
모두 host 네트워크에서 `127.0.0.1` 에만 열립니다. 밖에서는 nginx 의 `https://grafana.<도메인>` 으로 Grafana 만 보입니다.

| 이름 | 주소 (서버 안) | 하는 일 | 보관 |
| --- | --- | --- | --- |
| Prometheus | `127.0.0.1:9090` | 앱 `/actuator/prometheus` 를 15초마다 수집 (배포된 환경만, 아래 수집 대상) | 15일 |
| Loki | `127.0.0.1:3100` | 앱 컨테이너 로그 저장 | 14일 |
| Alloy | `127.0.0.1:12345` | docker 에서 dev · prod 앱 로그를 읽어 Loki 로 보냄 | - |
| Grafana | `127.0.0.1:3000` | 대시보드 · 로그 검색 · 알림 | - |

데이터소스 · 대시보드 · 알림 규칙 · Slack 연결은 `grafana/` 아래 파일로 관리합니다.
JVM 대시보드(`grafana/dashboards/jvm-micrometer.json`)는 커뮤니티 대시보드 [4701](https://grafana.com/grafana/dashboards/4701) 리비전 10 을 받아
데이터소스를 uid `prometheus` 로 고정하고, 이 앱에 없는 `application` 라벨 조건을 `job="kidly"` 로 바꾼 것입니다.
Spring Boot 대시보드(`grafana/dashboards/spring-boot.json`, HikariCP · HTTP · GC · Logback)는 커뮤니티 대시보드 [19004](https://grafana.com/grafana/dashboards/19004) 리비전 1 을 받아
데이터소스를 uid `prometheus` 로 고정하고, 이 앱에 없는 `application` · `namespace` 변수와 `instance` 변수를 빼고 `env` 변수 하나로 거르게(`job="kidly", env="$env"`) 바꾼 것입니다.
INFO logs 패널에 섞여 있던 전체 레벨 누적값 쿼리도 뺐습니다.

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

## 알림 메시지

Slack 메시지 형식은 `grafana/provisioning/alerting/templates.yml` 에 있습니다. 제목은 알림 이름 · 환경, 본문은 규칙의 `summary` · `description` 입니다.

```
🔴 앱 다운 · dev                        ← warning 은 🟠 주황, 그 밖은 빨강. 같은 그룹에 여러 건이면 끝에 (N건)
앱 메트릭을 2분 넘게 못 읽었습니다 (앱 다운 또는 /actuator/prometheus 없음)
규칙 보기 · 일시정지

🟢 해결 · 앱 다운 · dev                  ← 초록
정상으로 돌아왔습니다
규칙 보기
```

Prometheus 조회가 실패하면 알림 이름이 `DatasourceError` 로 오는데, 제목에는 규칙 이름을 써서 `🔴 앱 다운 · 조회 실패` 로 보입니다(데이터가 없을 때는 `· 데이터 없음`).

"일시정지" 는 그 알림의 라벨로 silence 를 만드는 화면을 엽니다.

## 설정 바꾸기

레포의 파일을 고쳐 `develop` 에 머지하면 워크플로가 다시 배포합니다. 레포에서 지운 설정 파일은 서버에서도 지워집니다.

비밀을 바꿀 때는 Secret 을 고친 뒤(`gh secret set ... --env dev`) 워크플로를 손으로 실행합니다.
**Grafana 관리자 비밀번호는 Secret 이 원본입니다. Grafana 화면에서 바꾸지 마세요.** 배포마다 Secret 값으로 맞춰지므로 화면에서 바꾼 비밀번호는 다음 배포 때 되돌아갑니다.

Grafana 화면에서 고친 대시보드 · 알림 규칙은 저장되지 않거나 다음 배포 때 파일 내용으로 돌아갑니다. 바꿀 것은 레포에서 고칩니다.

## 수집 대상

앱 배포(`deploy/deploy.sh`)가 앱이 healthy 로 뜰 때마다 `/opt/app/monitoring/targets/<환경>.yml` 에 `127.0.0.1:<APP_PORT>` 와 `env` 라벨을 써 둡니다.
Prometheus 는 이 폴더를 읽어 대상을 바꾸므로 손으로 고칠 것이 없습니다. prod 도 첫 배포 때 수집 · 알림이 시작됩니다.
기동에 실패한 배포는 파일을 바꾸지 않습니다. 이미 있던 환경이면 "앱 다운" 이 그대로 울립니다.
로그는 Alloy 가 앱 컨테이너를 알아서 찾습니다.

이 방식으로 바뀐 뒤 첫 앱 배포가 dev 를 등록합니다. 그 전에는 수집 대상이 없어 아무 알림도 오지 않습니다.

```bash
cat /opt/app/monitoring/targets/*.yml
curl -s 127.0.0.1:9090/api/v1/targets | grep -o '"scrapeUrl":"[^"]*"'
```

환경을 없앨 때는 그 환경의 파일(`targets/<환경>.yml`)을 지웁니다. 남겨 두면 "앱 다운" 이 계속 울립니다.

## 로그 보기

Kidly 폴더의 **Kidly 로그** 대시보드에서 상태 코드별 요청 수 · 요청 로그 · ERROR 로그 · 느린 요청(1초 넘음)을 봅니다.
직접 찾을 때는 Grafana → Explore → 데이터소스 Loki 에서 검색합니다.

서버(dev · prod) 콘솔 로그는 ECS JSON 한 줄입니다. 라벨은 `env` · `level` 이고, `traceId` 는 구조화 메타데이터라 `| traceId="..."` 로 거릅니다.
로그 줄을 펼치면 traceId 옆 "같은 요청 로그 보기" 링크로 그 요청의 로그를 모두 엽니다(Explore, 최근 24시간).
나머지 필드는 `| json` 으로 펼쳐 거릅니다. 점은 `_` 로 바뀝니다.

| JSON 키 | `\| json` 뒤 이름 | 예 |
| --- | --- | --- |
| `log.level` · `log.logger` | `log_level` · `log_logger` | `ERROR` · `com.bbcc.kidly...` |
| `message` | `message` | `<<< GET /api/products - 200 (2ms)` |
| `http.request.method` | `http_request_method` | `GET` |
| `url.path` | `url_path` | `/api/products` |
| `http.response.status_code` | `http_response_status_code` | `200` |
| `duration_ms` | `duration_ms` | `2` |
| `error.type` · `error.message` · `error.stack_trace` | `error_type` · `error_message` · `error_stack_trace` | 예외가 있을 때 |
| `process.thread.name` | `process_thread_name` | `http-nio-8080-exec-1` |

요청 필드(method · path · status · duration)는 요청 끝 로그(`<<<`)에만 있습니다. `traceId` 는 요청 안에서 남긴 로그에만 있습니다.

```
{env="dev", level="ERROR"}                                        # ERROR 로그 (스택 트레이스 포함)
{env="dev"} | traceId="3e81d408-302d-44fa-8ee3-bcc817933f7b"      # 한 요청의 로그 전부
{env="dev"} |= "<<<" | json | http_response_status_code >= 500    # 5xx 요청
{env="dev"} |= "<<<" | json | duration_ms > 1000                  # 1초 넘은 요청
{env="dev"} |= "<<<" | json | url_path = "/api/products"
sum by (http_response_status_code) (count_over_time({env="dev"} |= "<<<" | json | __error__="" [5m]))
```

수를 세는 쿼리(`count_over_time` 등)는 `| json` 뒤에 `__error__=""` 를 붙입니다. JSON 이 아닌 줄(기동 배너, JSON 이전 이미지의 텍스트 로그)이 섞이면 쿼리가 실패합니다.
JSON 이전 이미지의 텍스트 로그도 `level` 라벨로는 그대로 찾을 수 있고, 스택 트레이스는 로그 한 건으로 묶여 있습니다.

## 알려진 한계

- **모니터링 자체가 죽으면 알림도 멈춥니다.** Grafana 가 알림을 보내므로 Grafana · Prometheus 가 죽거나 EC2 가 꺼지면 알릴 수 없습니다. 감지되는 것은 `monitoring-deploy` 실패 알림(`#4팀-알림`)뿐입니다.
- **앱이 막 재시작한 직후의 ERROR 는 건수 알림에서 빠질 수 있습니다.** 첫 수집(15초) 전에 난 ERROR 는 카운터에 잡히지 않습니다. 로그(Loki)에는 남습니다.
- **Alloy 는 로그를 읽으려고 `/var/run/docker.sock` 을 마운트합니다.** `:ro` 여도 Docker API 는 막지 못해, Alloy 가 침해되면 서버 root 와 같은 권한이 됩니다. 127.0.0.1 에만 열고 원격 설정을 쓰지 않는 것으로 위험을 줄였습니다. 더 줄이려면 `docker-socket-proxy` 로 `containers` · `events` 만 열 수 있습니다.
