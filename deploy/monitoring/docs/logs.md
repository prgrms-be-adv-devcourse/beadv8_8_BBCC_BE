# 로그 검색 상세

[운영 안내로 돌아가기](../README.md)

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
각자 열을 더 고르면 그 브라우저에만 남습니다. 이 기능은 Grafana 12.4 이상 · 기능 플래그 `kubernetesLogsDrilldown`(`k8s/grafana.yaml` 의 `GF_FEATURE_TOGGLES_ENABLE`)이 있어야 합니다.
Logs Drilldown 앱 버전은 `k8s/grafana.yaml` 의 `GF_PLUGINS_PREINSTALL` 로 고정합니다. 앱은 Grafana 가 뜬 뒤 백그라운드로 받아 오므로, 받지 못하면 배포는 성공하고 `::warning::` 만 남습니다(기본 열이 안 보이면 monitoring-deploy 를 다시 돌림).

**Grafana 를 올릴 때 확인**: 앱 버전(`GF_PLUGINS_PREINSTALL`), 기능 플래그 `kubernetesLogsDrilldown`(실험 기능), 기본 열 API 경로 `apis/logsdrilldown.grafana.app/v1beta1/.../logsdrilldowndefaultcolumns`(`deploy.sh`). 경로가 바뀌면 `deploy.sh` 가 실패해 `#4팀-알림` 에 모니터링 배포 실패가 옵니다 — 컨테이너는 이미 새로 떠 있어 수집 · 알림은 그대로입니다.

서버(dev · prod) 콘솔 로그는 ECS JSON 한 줄입니다. 라벨은 `env` · `level` · `service_name`(앱 `kidly`, 검색 서비스 `kidly-search`, Gateway `kidly-gateway`, 자녀 프로필 서비스 `kidly-childprofile`, 추천 서비스 `kidly-recommend`, 정산 서비스 `kidly-settlement`, 회원 서비스 `kidly-member`, 결제 서비스 `kidly-payment`)이고, `traceId` · `memberId` 는 구조화 메타데이터라 `| traceId="..."` · `| memberId="..."` 로 거릅니다.
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

