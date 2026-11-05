# 로그 찾기

Grafana → **Kidly 로그**에서 환경·서비스·시간을 먼저 좁힙니다. 직접 검색은 Explore → Loki를 사용합니다.

| 찾을 것 | 방법 |
| --- | --- |
| 서비스 오류 | ERROR 패널 또는 `level="ERROR"` |
| 느린 요청 | 요청 로그에서 `duration_ms > 1000` |
| 같은 요청 | traceId 링크 또는 `traceId` 구조화 메타데이터 필터 |
| 특정 API | `http.route` 정규화 경로로 검색 |

## 기본 요청 로그

#779 필터가 배포된 이미지부터 method·정규화 route·status·duration·traceId만 남깁니다.
기존 화면 호환을 위해 `url.path`도 유지하되 값은 **정규화 route**입니다. 경로를 알 수 없으면 `UNKNOWN`이며 원래 URL로 대체하지 않습니다.
요청/응답 본문·헤더·query·IP·User-Agent·회원/관리자/자녀 ID는 기본 요청 로그에 넣지 않습니다.
본문을 조사해야 할 때는 [요청 로그 정책](request-log-policy.md)을 따릅니다. 이 필터가 모든 도메인의 별도 로그까지 정제하지는 않습니다.

```logql
{env="dev", service_name="kidly-search", level="ERROR"}
{env="dev", service_name="kidly-search"} | traceId="00000000-0000-4000-8000-000000000001"
{env="dev", service_name="kidly-search"} |= "RequestLoggingFilter" | json | __error__="" | http_response_status_code >= 500
{env="dev", service_name="kidly-search"} |= "RequestLoggingFilter" | json | __error__="" | duration_ms > 1000
{env="dev", service_name="kidly-search"} |= "RequestLoggingFilter" | json | __error__="" | http_route = "/api/v1/orders/{id}"
```

`| json` 뒤 `__error__=""`로 JSON이 아닌 기동 로그를 제외합니다. 통계 쿼리는 필요한 필드만 추출해 불필요한 라벨 증가를 피합니다.
traceId는 인덱스 라벨이 아닌 구조화 메타데이터입니다. 주문·회원 ID를 새 지표 라벨로 넣지 않습니다.

## Drilldown

Grafana → Drilldown → Logs에서 서비스를 고르고 Fields로 필터링합니다.
기본 열은 `grafana/logs-drilldown-default-columns.json`으로 관리하며, 배포 시 기존 Grafana API로 적용합니다.
사용자가 고른 추가 열은 브라우저에 남을 수 있습니다. Grafana 업그레이드 때 플러그인 버전·기능 플래그·기본 열 API 경로 호환을 확인합니다.

과거 이미지가 저장한 로그에는 예전 필드가 남아 있을 수 있습니다. 이 변경은 과거 데이터 삭제·보관 기간 변경을 하지 않습니다.
원문 로그·개인정보·토큰을 PR·Slack 알림·AI에 복사하지 않습니다.
