# AI 운영 분석 메모

Grafana → Kidly → **Kidly AI 운영 분석**. 최신 메모 행을 펼쳐 근거·원인 후보·확인 순서·대응 제안을 봅니다.
**실제 추론은 MCP를 사용하는 AI 클라이언트가 합니다.** 서버는 지표 발급·검증·게시만 하며 AI API를 호출하지 않습니다.
현재 구현은 로컬 검증 단계이며, 게시 전용 SSH 접근과 PC MCP 활성화는 별도 확인 후 적용합니다.

## 사용 흐름

1. AI가 `get_analysis_context(environment)`로 발급 문맥과 deadline을 받습니다
2. AI가 실제로 추론하고 `AnalysisReport` 형식으로 결과를 만듭니다
3. `publish_analysis(context_id, report)`로 제출합니다. 원래 관측값·호출자·근거·형식을 검증한 메모만 게시합니다
4. `get_publication_status(context_id)`로 영수증/전송 상태를 확인합니다

외부 AI나 로컬 모델은 같은 입력/출력 계약을 사용합니다. 모두 같은 HTTP API를 쓴다고 가정하지 않습니다.
서버 worker용 `AnalysisProvider.analyze(context, deadline)`도 별도 어댑터로 구현할 수 있지만 현재 구현하지 않았습니다.
MCP는 도구 연결 규약이며 모델 자체가 아닙니다. 다른 제공자에 지표를 전달할 때는 별도 반출 승인이 필요합니다.

## 안전 경계

- 기존 `kidly_grafana`의 Viewer·메트릭 조회 권한은 유지합니다. 새 게시용 키는 별도이며 다른 서비스/SSH 명령을 실행할 수 없어야 합니다
- 서버가 localhost Prometheus의 고정 쿼리로 dev/prod 문맥을 발급합니다. 로그·원본 라벨·식별값·임의 PromQL은 입력으로 받지 않습니다
- 지표는 환경/서비스 차원과 단위를 가집니다. 미수집/중지/저트래픽 등 원래 문맥의 제약은 AI가 빼더라도 메모에 보존합니다
- 문맥은 인증된 principal에 묶고 300초 안에 제출해야 합니다. deadline은 절대 Unix 초입니다. 만료 후 새 게시는 거부하고 영수증 조회만 허용합니다
- provider/model/생성 시각은 자기 신고이며, 인증 주체·관측/게시 시각과 구분해 표시합니다. 형식 검증이 AI 환각을 완전히 막지는 않습니다
- 입력 최대 50KB, 메모 최대 100KB, receiver 30초·CPU 10초·메모리 96MiB 제한. 문맥 발급은 호출자별 30초 간격입니다
- 새 포트·이미지·패키지·계정·상시 데몬·예약 작업은 없습니다. 앱·DB·Kafka·offset·운영시간을 바꾸지 않습니다

## 저장·실패

`/opt/app/monitoring/ai-notes-state/notes.sqlite3`(0600, 디렉터리 0700)에 문맥과 outbox를 보관합니다.
전송 전에 결과를 보존하고 동일 context/digest는 같은 영수증을 반환합니다. 다른 내용은 충돌로 거부합니다.
실패 후 같은 결과를 deadline 안에 재제출하면 저장된 동일 timestamp/본문을 전송합니다. AI를 다시 호출하지 않습니다.
만료된 미전송 건은 자동 재분석/재전송하지 않고 남깁니다. 운영자가 저장된 outbox와 Loki 상태를 확인해야 합니다.
전송 완료 기록/문맥은 새 발급 시 14일 경과분을 정리하고, 미전송 outbox는 자동 삭제하지 않습니다. 문맥 상한은 2,000개입니다.

Loki에는 고정 `job=kidly-ai-notes`와 env 라벨만 사용하고 분석 텍스트는 JSON 로그 본문으로 저장합니다(기존 14일 보관).
Grafana 기본 logs 패널이 30초마다 최근 메모를 조회합니다. 모델 문자열은 HTML로 실행하지 않습니다.
기존 Alloy textfile 경로에 관측 시각·미전송 건수만 기록합니다. 오래된 메모/미전송 상태는 서비스 정상 판정이 아닙니다.

## 게시 접근 설치 전 확인

기존 배포가 코드를 올리고 로컬 DB/상태 지표만 초기화합니다. SSH 키를 생성·등록하거나 PC MCP를 켜지는 않습니다.
서버 관리자는 승인된 공개키에만 아래 형태의 한정 접근을 추가합니다(실제 값은 Git에 넣지 않음).

```text
restrict,expiry-time="<7일 이내 UTC>",command="/usr/bin/python3 /opt/app/monitoring/ai-notes/receive.py --principal <별칭>" ssh-ed25519 <공개키>
```

클라이언트는 `kidly-ai-notes`라는 고정 command marker만 요청합니다. 다른 명령·대화형 셸·파일 전송·포워딩은 거부돼야 합니다.
PC 개인키는 전용 macOS Keychain 항목에 base64로 보관합니다. 연결 중에만 0600 임시 파일로 쓰고 종료/오류 때 지웁니다.
MCP 설정은 서버 host·deploy user·Keychain 참조·검증된 known_hosts 경로만 담습니다. 기존 관리자 SSH 키를 MCP에 주지 않습니다.
macOS 외 환경은 자격증명/SSH adapter를 별도로 구현하고 AI 분석 계약은 유지합니다.

`mcp_server.py --config <설정 JSON>`을 stdio로 실행합니다. 예시는 `connection.example.json`입니다.
해제는 PC MCP 비활성화 → 해당 공개키 한 줄 제거 → 전용 Keychain 항목 폐기 순서입니다. 다른 SSH 키·데이터는 삭제하지 않습니다.

검증: `python3 -m unittest discover -s deploy/monitoring/tests -p 'test_*.py'`
실제 연결 후 읽기·게시·영수증 재조회, 타 명령/파일전송/포워딩 거부, 실패·오래됨·접기/펼치기를 확인합니다.
