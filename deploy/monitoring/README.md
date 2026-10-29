# 모니터링 (Prometheus · Loki · Grafana)

EC2 안에서 앱의 메트릭과 로그, 호스트 · 컨테이너 자원 사용량을 모으고 Grafana 로 봅니다. 앱 다운 · 5xx 증가 · ERROR 로그는 Slack `#4팀-운영이슈` 로 알립니다.
모두 host 네트워크에서 `127.0.0.1` 에만 열립니다. 밖에서는 nginx 의 `https://grafana.<도메인>` 으로 Grafana 만 보입니다.

| 이름 | 주소 (서버 안) | 하는 일 | 보관 |
| --- | --- | --- | --- |
| Prometheus | `127.0.0.1:9090` | 앱 `/actuator/prometheus` 를 15초마다 수집 (배포된 환경만, 아래 수집 대상). Alloy 가 보내는 호스트 · 컨테이너 메트릭을 remote write 로 받음 | 15일 |
| Loki | `127.0.0.1:3100` | 앱 컨테이너 로그 저장 | 14일 |
| Alloy | `127.0.0.1:12345` | docker 에서 dev · prod 앱 로그를 읽어 Loki 로 보냄. 내장 node_exporter · cAdvisor 로 호스트(CPU · 메모리 · 디스크 · 네트워크) · 컨테이너별 자원을 15초마다 Prometheus 로 보냄 | - |
| Grafana | `127.0.0.1:3000` | 대시보드 · 로그 검색 · 알림 | - |

데이터소스 · 대시보드 · 알림 규칙 · Slack 연결은 `grafana/` 아래 파일로 관리합니다.
JVM 대시보드(`grafana/dashboards/jvm-micrometer.json`)는 커뮤니티 대시보드 [4701](https://grafana.com/grafana/dashboards/4701) 리비전 10 을 받아
데이터소스를 uid `prometheus` 로 고정하고, 이 앱에 없는 `application` 라벨 조건을 `job="kidly"` 로 바꾼 것입니다.
Spring Boot 대시보드(`grafana/dashboards/spring-boot.json`, HikariCP · HTTP · GC · Logback)는 커뮤니티 대시보드 [19004](https://grafana.com/grafana/dashboards/19004) 리비전 1 을 받아
데이터소스를 uid `prometheus` 로 고정하고, 이 앱에 없는 `application` · `namespace` 변수와 `instance` 변수를 빼고 `env` 변수 하나로 거르게(`job="kidly", env="$env"`) 바꾼 것입니다.
INFO logs 패널에 섞여 있던 전체 레벨 누적값 쿼리도 뺐습니다.
서버 대시보드(`grafana/dashboards/kidly-server.json`)는 호스트 CPU · 메모리 · 디스크 · 업타임과 디스크 I/O · 네트워크, 컨테이너별(앱 · Postgres · Kafka · 모니터링) CPU · 메모리를 한 화면에 보여 줍니다. 변수 없이 고정 패널입니다.

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

🟠 ERROR 로그 · dev
최근 5분 ERROR 로그 3건
규칙 보기 · 일시정지 · 로그 보기

🟢 해결 · 앱 다운 · dev                  ← 초록
정상으로 돌아왔습니다
규칙 보기
```

Prometheus 조회가 실패하면 알림 이름이 `DatasourceError` 로 오는데, 제목에는 규칙 이름을 써서 `🔴 앱 다운 · 조회 실패` 로 보입니다(데이터가 없을 때는 `· 데이터 없음`).

"일시정지" 는 그 알림의 라벨로 silence 를 만드는 화면을 엽니다.
5xx 증가 · ERROR 로그 알림에는 "로그 보기" 가 더 붙습니다. 그 환경의 최근 15분 5xx 요청 로그 · ERROR 로그를 Explore 로 엽니다(규칙의 `logs_url`).

## 설정 바꾸기

레포의 파일을 고쳐 `develop` 에 머지하면 워크플로가 다시 배포합니다. 레포에서 지운 설정 파일은 서버에서도 지워집니다.

비밀을 바꿀 때는 Secret 을 고친 뒤(`gh secret set ... --env dev`) 워크플로를 손으로 실행합니다.
**Grafana 관리자 비밀번호는 Secret 이 원본입니다. Grafana 화면에서 바꾸지 마세요.** 배포마다 Secret 값으로 맞춰지므로 화면에서 바꾼 비밀번호는 다음 배포 때 되돌아갑니다.

Grafana 화면에서 고친 대시보드 · 알림 규칙은 저장되지 않거나 다음 배포 때 파일 내용으로 돌아갑니다. 바꿀 것은 레포에서 고칩니다.

## 수집 대상

앱 배포(`deploy/deploy.sh`)가 앱이 healthy 로 뜰 때마다 `/opt/app/monitoring/targets/<환경>.yml` 에 `127.0.0.1:<APP_PORT>` 와 `env` 라벨을 써 둡니다.
Prometheus 는 이 폴더를 읽어 대상을 바꾸므로 손으로 고칠 것이 없습니다. prod 도 첫 배포 때 수집 · 알림이 시작됩니다.
기동에 실패한 배포는 파일을 바꾸지 않습니다. 이미 있던 환경이면 "앱 다운" 이 그대로 울립니다.
로그는 Alloy 가 앱 컨테이너를 알아서 찾습니다. 호스트 · 컨테이너 메트릭도 Alloy 가 모아 Prometheus 에 넣으므로(job `integrations/unix` · `integrations/cadvisor`) 등록할 것이 없습니다.

이 방식으로 바뀐 뒤 첫 앱 배포가 dev 를 등록합니다. 그 전에는 수집 대상이 없어 아무 알림도 오지 않습니다.

```bash
cat /opt/app/monitoring/targets/*.yml
curl -s 127.0.0.1:9090/api/v1/targets | grep -o '"scrapeUrl":"[^"]*"'
```

환경을 없앨 때는 그 환경의 파일(`targets/<환경>.yml`)을 지웁니다. 남겨 두면 "앱 다운" 이 계속 울립니다.

## 로그 보기

Kidly 폴더의 **Kidly 로그** 대시보드에서 상태 코드별 요청 수 · 요청 로그 · ERROR 로그 · 느린 요청(1초 넘음) · 요청 상세 표(시간 · method · route · status · duration · IP · memberId · traceId)를 봅니다.
직접 찾을 때는 Grafana → Explore → 데이터소스 Loki 에서 검색합니다. 결과 위의 **Table** 을 누르면 줄마다 한 행으로 필드를 열로 봅니다.

### Drilldown 으로 찾기

쿼리 없이 찾을 때는 Grafana → Drilldown → Logs → kidly 의 **Show logs** 를 누릅니다. 위 **Fields** 칸에서 필드 · 연산자 · 값을 골라 거르고, 여러 개를 넣으면 모두 맞는 줄만 나옵니다.

| 찾을 것 | Fields 칸 | 메모 |
| --- | --- | --- |
| 5xx | `http_response_status_code` `>= 500` (또는 `=~ 5..`) | 숫자 비교 · 정규식 둘 다 됩니다 |
| 특정 상태 | `http_response_status_code` `= 401` | |
| URL | `url_path` 또는 `http_route` `=~ .*orders.*` | 정규식은 값 전체와 맞아야 해서 부분 일치는 앞뒤에 `.*` |
| 회원 | 줄을 펼쳐 `memberId` 옆 돋보기(+) | 구조화 메타데이터라 `\| json` 앞에서 걸러 가볍습니다. Fields 칸의 `memberId = 7` 도 됩니다 |
| 한 요청 | 표의 `traceId` 를 누르면 "같은 요청 로그 보기"(Explore). 줄을 펼쳐 `traceId` 옆 돋보기(+) 도 됩니다 | |

위 **Line filter** 칸은 줄 전체에서 글자를 찾습니다(특정 필드가 아님).
결과 위 **Table** 을 누르면 아래 열이 기본으로 골라져 있습니다. 필터를 바꿔도 열은 그대로입니다. 앞의 Time · Line · detected_level 은 Drilldown 이 늘 붙이는 열입니다.

`http_request_method` · `http_route` · `url_path` · `http_response_status_code` · `duration_ms` · `client_ip` · `memberId` · `traceId` · `message`

기본 열의 원본은 `grafana/logs-drilldown-default-columns.json`(데이터소스 `loki`, `service_name=kidly`)입니다. 배포 때 `deploy.sh` 가 Grafana API 로 넣으므로 화면(관리 → 플러그인 → Logs Drilldown → Default fields)에서 바꾼 것은 다음 배포 때 파일 내용으로 돌아갑니다.
각자 열을 더 고르면 그 브라우저에만 남습니다. 이 기능은 Grafana 12.4 이상 · 기능 플래그 `kubernetesLogsDrilldown`(compose 의 `GF_FEATURE_TOGGLES_ENABLE`)이 있어야 합니다.
Logs Drilldown 앱 버전은 compose 의 `GF_PLUGINS_PREINSTALL` 로 고정합니다. 앱은 Grafana 가 뜬 뒤 백그라운드로 받아 오므로, 받지 못하면 배포는 성공하고 `::warning::` 만 남습니다(기본 열이 안 보이면 monitoring-deploy 를 다시 돌림).

**Grafana 를 올릴 때 확인**: 앱 버전(`GF_PLUGINS_PREINSTALL`), 기능 플래그 `kubernetesLogsDrilldown`(실험 기능), 기본 열 API 경로 `apis/logsdrilldown.grafana.app/v1beta1/.../logsdrilldowndefaultcolumns`(`deploy.sh`). 경로가 바뀌면 `deploy.sh` 가 실패해 `#4팀-알림` 에 모니터링 배포 실패가 옵니다 — 컨테이너는 이미 새로 떠 있어 수집 · 알림은 그대로입니다.

서버(dev · prod) 콘솔 로그는 ECS JSON 한 줄입니다. 라벨은 `env` · `level` · `service_name`(항상 `kidly`)이고, `traceId` · `memberId` 는 구조화 메타데이터라 `| traceId="..."` · `| memberId="..."` 로 거릅니다.
로그 줄을 펼치면 traceId 옆 "같은 요청 로그 보기" 링크로 그 요청의 로그를 모두 엽니다(Explore, 최근 24시간).
나머지 필드는 `| json` 으로 펼쳐 거릅니다. 점은 `_` 로 바뀝니다.

| JSON 키 | `\| json` 뒤 이름 | 예 |
| --- | --- | --- |
| `log.level` · `log.logger` | `log_level` · `log_logger` | `ERROR` · `com.bbcc.kidly...` |
| `message` | `message` | `GET /api/products - 200 (2ms)` |
| `http.request.method` | `http_request_method` | `GET` |
| `url.path` | `url_path` | `/api/products` |
| `http.response.status_code` | `http_response_status_code` | `200` |
| `duration_ms` | `duration_ms` | `2` |
| `http.route` | `http_route` | `/api/v1/products/{id}` |
| `url.query` | `url_query` | `page=1&token=***` |
| `client.ip` | `client_ip` | `203.0.113.7` |
| `user_agent.original` | `user_agent_original` | `Mozilla/5.0 ...` |
| `http.request.headers.<이름>` · `http.response.headers.<이름>` | `http_request_headers_<이름>` · `http_response_headers_<이름>` | 헤더 이름은 소문자 |
| `http.request.body.content` · `http.response.body.content` | `http_request_body_content` · `http_response_body_content` | JSON 본문, 각 UTF-8 3,000바이트 |
| `memberId` | (구조화 메타데이터) | `7` |
| `error.type` · `error.message` · `error.stack_trace` | `error_type` · `error_message` · `error_stack_trace` | 예외가 있을 때 |
| `process.thread.name` | `process_thread_name` | `http-nio-8080-exec-1` |

요청 로그는 요청이 끝날 때 `RequestLoggingFilter` 가 한 줄 남기고, 요청 필드(method · path · status · duration · IP · 헤더 · 본문 등)는 이 줄에만 있습니다. `traceId` 는 요청 안에서 남긴 로그에만, `memberId` 는 로그인한 요청 안에서 남긴 로그에만 있습니다.
가림: 헤더 `Authorization` · `Cookie` · `Set-Cookie` · `Proxy-Authorization` 과 이름에 아래 말이 든 헤더(`X-Auth-Token` · `X-Api-Key` 등), 쿼리 · JSON 본문의 비밀값(password · token · secret · apiKey · paymentKey · uploadUrl · 사업자등록번호 registrationNumber 계열), 연락처 · 주소(phone · email · address · zipCode 계열), 자녀 생년월일 · 키 · 몸무게(`birthDate` · `heightCm` · `weightKg`, 이름이 같을 때만) 값은 `***` 입니다. 이름 · ID · 금액은 남깁니다.
본문을 남기지 않는 것: `/api/v1/auth/**` · `/api/v1/dev/tokens` · `/api/v1/webhooks/toss`, JSON 이 아닌 본문(텍스트는 가릴 방법이 없어 생략, multipart · 바이너리), 비동기 응답. 64KB 가 넘는 응답 본문은 `(N바이트, 생략)` 만 남깁니다. 본문은 UTF-8 3,000바이트(`…(잘림, 전체 N바이트)`), 헤더 값은 300자(`…(잘림, 전체 N자)`)를 넘으면 잘립니다.
요청 로그 쿼리는 로거 이름으로 먼저 좁혀(`|= "RequestLoggingFilter"`) `| json` 할 줄을 줄이고, 요청 필드가 있는 줄만 고릅니다(`http_request_method != ""`).

```
{env="dev", level="ERROR"}                                        # ERROR 로그 (스택 트레이스 포함)
{env="dev"} | traceId="3e81d408-302d-44fa-8ee3-bcc817933f7b"      # 한 요청의 로그 전부
{env="dev"} |= "RequestLoggingFilter" | json | __error__="" | http_request_method != ""         # 요청 로그
{env="dev"} |= "RequestLoggingFilter" | json | __error__="" | http_response_status_code >= 500  # 5xx 요청
{env="dev"} |= "RequestLoggingFilter" | json | __error__="" | duration_ms > 1000                # 1초 넘은 요청
{env="dev"} |= "RequestLoggingFilter" | json | __error__="" | url_path = "/api/products"
{env="dev"} |= "RequestLoggingFilter" | json | __error__="" | http_route = "/api/v1/products/{id}"
{env="dev"} |= "RequestLoggingFilter" | json | __error__="" | client_ip = "203.0.113.7"     # 한 IP 의 요청
{env="dev"} | memberId="7"                                                                 # 한 회원의 로그 전부
sum by (http_response_status_code) (count_over_time({env="dev"} |= "RequestLoggingFilter" | json http_request_method="http.request.method", http_response_status_code="http.response.status_code" | __error__="" | http_request_method != "" [5m]))
```

인자 없는 `| json` 은 헤더 · 본문까지 모든 필드를 펼칩니다. 한 줄을 찾아 볼 때는 편하지만, 많은 줄을 세거나 대시보드처럼 자주 도는 쿼리는 필요한 필드만 뽑습니다(`| json 이름="JSON.경로", ...`, 위 마지막 줄 · Kidly 로그 대시보드).

수를 세는 쿼리(`count_over_time` 등)는 `| json` 뒤에 `__error__=""` 를 붙입니다. JSON 이 아닌 줄(기동 배너, JSON 이전 이미지의 텍스트 로그)이 섞이면 쿼리가 실패합니다.
JSON 이전 이미지의 텍스트 로그도 `level` 라벨로는 그대로 찾을 수 있고, 스택 트레이스는 로그 한 건으로 묶여 있습니다.

## 알려진 한계

- **모니터링 자체가 죽으면 알림도 멈춥니다.** Grafana 가 알림을 보내므로 Grafana · Prometheus 가 죽거나 EC2 가 꺼지면 알릴 수 없습니다. 감지되는 것은 `monitoring-deploy` 실패 알림(`#4팀-알림`)뿐입니다.
- **앱이 막 재시작한 직후의 ERROR 는 건수 알림에서 빠질 수 있습니다.** 첫 수집(15초) 전에 난 ERROR 는 카운터에 잡히지 않습니다. 로그(Loki)에는 남습니다.
- **Alloy 는 로그를 읽으려고 `/var/run/docker.sock` 을 마운트합니다.** `:ro` 여도 Docker API 는 막지 못해, Alloy 가 침해되면 서버 root 와 같은 권한이 됩니다. 127.0.0.1 에만 열고 원격 설정을 쓰지 않는 것으로 위험을 줄였습니다. 더 줄이려면 `docker-socket-proxy` 로 `containers` · `events` 만 열 수 있습니다. 컨테이너 메트릭(cAdvisor)을 위해 `privileged` 로 돌고 호스트 `/` · `/proc` · `/sys` 를 읽기 전용으로 마운트하는데, docker.sock 으로 이미 같은 권한이라 더 넓어지는 것은 아닙니다.
- **컨테이너 메트릭은 로컬(Docker Desktop)에서는 안 나옵니다.** Docker Desktop 은 containerd 이미지 저장소를 써서 cAdvisor 가 컨테이너를 못 읽습니다(`failed to identify the read-write layer ID`). 서버(overlay2)에서는 나옵니다. 호스트 메트릭은 로컬에서도 Docker VM 기준으로 나옵니다.
