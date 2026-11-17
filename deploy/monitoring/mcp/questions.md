# AI로 지표 물어보기

MCP(`kidly_grafana`)를 연결한 AI 클라이언트(Codex · Claude Code)에게 운영 지표를 물어볼 때 쓰는 질문 모음입니다. 연결 방법은 [README](README.md)를 봅니다.

- AI는 **Prometheus 지표만** 조회합니다. 로그 · 대시보드 · 알림은 볼 수 없으니 예외 메시지는 Grafana **4 · Kidly 로그**에서 직접 봅니다
- 한 번에 볼 수 있는 시간 범위는 **최근 1시간**(범위 쿼리 최대 1시간 · 시계열당 241점)입니다. "어제부터"처럼 긴 범위는 Grafana 화면에서 봅니다
- 운영 환경 라벨은 `env="dev"`, 서비스 라벨은 `job="kidly-<서비스>"`입니다(예: `kidly-order`)
- AI 답은 확인 전 가설입니다. 아래 "대조할 화면"에서 같은 시각을 열어 확인합니다

## 질문을 잘 쓰는 법

AI가 추측하지 않도록 세 가지를 넣습니다.

1. **대상**: 서비스 이름(`kidly-order`), 환경(운영 dev)
2. **시각**: "10:30 전후 30분", "지금부터 최근 1시간"
3. **근거로 쓸 지표**: 아래 표의 "AI가 볼 지표"를 그대로 붙여도 됩니다

```text
운영(dev) kidly-order를 최근 1시간 동안 봐 줘. query_prometheus로 경로별 5xx(http_server_requests_seconds_count, status=~"5..", uri!~"/actuator.*"),
같은 시각 재시작(process_start_time_seconds), DB 연결 대기(hikaricp_connections_pending)를 조회하고,
숫자 근거와 원인 후보(가설), Grafana에서 확인할 화면 이름을 알려 줘.
```

## 자주 쓰는 질문

| 질문 | AI가 볼 지표(PromQL) | 대조할 화면 |
|---|---|---|
| 지금 어느 서비스가 이상해? | `max by (job) (up{env="dev"})` · `sum by (job) (rate(http_server_requests_seconds_count{env="dev",uri!~"/actuator.*",status=~"5.."}[5m]))` · `sum by (job) (increase(logback_events_total{env="dev",level="error"}[5m]))` | 1 · 운영 홈 상태판 |
| ○○ 서비스에서 어느 API가 오류를 내? | `topk(10, sum by (method, uri, status) (increase(http_server_requests_seconds_count{env="dev",job="kidly-○○",status=~"5..\|4.."}[1h])))` | 3 · 서비스 오류 → 오류가 난 API 표 |
| ○○ 서비스가 언제부터 느려졌어? | `sum by (uri) (rate(http_server_requests_seconds_sum{env="dev",job="kidly-○○",uri!~"/actuator.*\|/api/v1/(child/)?notifications/stream"}[5m])) / sum by (uri) (rate(http_server_requests_seconds_count{env="dev",job="kidly-○○",uri!~"/actuator.*\|/api/v1/(child/)?notifications/stream"}[5m]))` (범위 쿼리) | 2 · 서비스 상세 → 경로별 평균 응답 |
| 최근에 재시작한 서비스 있어? | `max by (job, instance) (process_start_time_seconds{env="dev"})` (현재 시각과 비교) | 2 · 서비스 상세 → 재시작 표시선 |
| DB 커넥션이 모자라? | `sum by (job, pool) (hikaricp_connections_pending{env="dev"})` · `hikaricp_connections_active / hikaricp_connections_max` | 2 · 서비스 상세 → DB 커넥션 풀(펼치기) |
| 메모리 · GC가 문제야? | `sum by (job) (jvm_memory_used_bytes{env="dev",area="heap"})` · `sum by (job) (rate(jvm_gc_pause_seconds_sum{env="dev"}[5m]))` | 2 · 서비스 상세 → JVM(펼치기) |
| Kafka 이벤트가 밀려 있어? | `sum by (consumergroup, topic) (kafka_consumergroup_lag{job="kidly-kafka",env="dev"} >= 0)` | 6 · Kafka 처리, 2 · 서비스 상세 → Kafka 소비(펼치기) |
| Gateway 오류가 하위 서비스 때문이야? | Gateway 5xx: `sum by (status) (rate(http_server_requests_seconds_count{env="dev",job="kidly-gateway",status=~"5.."}[5m]))` · 같은 시각 하위 서비스별 5xx와 `up` | 3 · 서비스 오류(서비스 All) |
| 서버 자원이 부족해? | `node_memory_MemAvailable_bytes` · `1 - avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m]))` | 7 · 서버 |
| 부하 테스트 동안 서버는 어땠어? | `sum by (job) (rate(http_server_requests_seconds_count{env="dev"}[1m]))` · 위 5xx · 평균 응답 · `process_cpu_usage` (테스트 시각 1시간 안) | 8 · 부하 테스트 |

## 해석할 때 주의

- **Gateway**는 하위 서비스로 넘기는 요청이 모두 `uri="UNKNOWN"`으로 잡혀 경로를 나눌 수 없고, 하위 서비스 5xx를 그대로 넘겨받습니다. Gateway와 하위 서비스 요청 수를 더하지 않습니다
- 실시간 알림 스트림(`/api/v1/notifications/stream` · `/api/v1/child/notifications/stream`)은 연결이 오래 열려 있어 평균 응답에서 뺍니다(Gateway는 뺄 수 없음)
- 서버 p95는 없습니다(앱 히스토그램 미수집). 응답 분포는 로그 기반 p95(4 · Kidly 로그 → API 처리 시간)나 k6 값으로 봅니다
- 지표가 없다는 것은 정상이라는 뜻이 아닙니다. 운영 중지(`kidly_environment_enabled`) · 운영 대상 목록(`kidly_app_expected`) · 수집 상태(`up`)를 먼저 봅니다
- 구독 그룹 `kidly.<이름>`은 서비스 `kidly-<이름>`입니다. member 서비스는 `auth` · `childprofile` · `member` · `seller` 그룹을 함께 씁니다
