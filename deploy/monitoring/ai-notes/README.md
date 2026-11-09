# AI 운영 분석 메모

Grafana → Kidly → **Kidly AI 운영 분석**. 최근 메모 행을 펼쳐 원인 후보·확인 순서·대응·근거를 봅니다.
**실제 AI 클라이언트가 MCP 지표로 추론합니다.** 서버는 문맥 발급·검증·게시만 하며 AI API를 호출하지 않습니다.
게시 전용 SSH로 실제 지표 조회 → AI 추론 → 게시 → 영수증 조회와 Grafana 표시를 검증했습니다. PC 연결은 전용 만료 키를 사용합니다.

## 흐름

1. `get_analysis_context(environment)`로 실제 지표와 deadline을 받습니다
   - 주문 운영 상태가 필요하면 주문 MCP(`order-mcp.example.toml`, Codex 설정 · dev 관리자 토큰)의 멈춘 주문 요약 · DLT · 아웃박스 격리 도구를 조회해 근거에 넣습니다([운영 안내](../docs/ai-notes-operations.md#주문-mcp-함께-쓰기1041))
2. AI가 추론해 공통 `AnalysisReport`를 만듭니다. 원인 가설을 사실과 구분합니다
3. `publish_analysis(context_id, report)`로 게시합니다
4. `get_publication_status(context_id)`로 영수증/전송 상태를 확인합니다

문맥 발급 후 300초 안에 제출합니다. 만료 후에는 영수증 조회만 허용합니다.
실패 시 같은 결과를 재사용하며 AI를 자동 재호출하거나 다른 유료 제공자로 전환하지 않습니다.

## 읽을 때

- 관측/게시 시각·근거·누락된 정보와 원인 후보를 함께 확인합니다
- 오래된 메모·미전송 상태는 서비스 정상 판정이 아닙니다. 최신 메모는 30초마다 조회합니다
- provider/model은 AI의 자기 신고입니다. 게시 경로의 호출자 기록과 구분합니다
- AI 문구는 plain text이며 실행하지 않습니다. 형식 검증이 환각/개인정보를 완전히 막지는 않습니다
- 내부 Loki 접속 주체가 같은 stream을 위조하는 것까지 암호학적으로 검증하는 화면은 아닙니다

## 교체 경계

`AnalysisContext → AnalysisProvider → AnalysisReport → NotePublisher`가 공통 계약입니다.
MCP 클라이언트 추론과 서버/로컬 모델 worker는 같은 결과 계약을 사용합니다. 후자의 실제 모델 호출 어댑터는 아직 없습니다.
모델을 바꿔도 Grafana 표시/게시 코드는 유지합니다. 다른 외부 AI에 지표를 보낼 때는 반출 승인을 확인합니다.

기존 MCP 경로는 별도 AI API를 호출하지 않습니다. 새 이미지·패키지·상시 서버·앱/DB/Kafka 변경은 없으며 기존 Grafana Viewer 권한은 유지합니다.
별도 제공자를 시험하려면 [Groq 무료 1회 분석](../docs/groq-trial.md)을 봅니다. 키 등록·Free 플랜 확인 후 dev에서 수동 실행하며 예약 실행은 없습니다.
설치·권한·실패 복구는 [운영 안내](../docs/ai-notes-operations.md)를 봅니다.
검증: `python3 -m unittest discover -s deploy/monitoring/tests -p 'test_*.py'`
