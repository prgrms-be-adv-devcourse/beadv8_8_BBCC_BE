# AI 메모 설치·운영

승인된 전용 만료 키로 SSH 제한·실제 AI 게시·영수증 조회·Grafana 표시를 검증했습니다. 새 PC 연결은 아래 설치 경계와 검증 절차를 따릅니다.

## 설치 경계

일반 모니터링 배포는 코드·화면과 로컬 SQLite/상태 지표만 초기화합니다. 실패해도 기존 모니터링 배포는 계속하고 경고합니다.
키 등록·PC MCP 활성화·모델 호출·예약 실행은 하지 않습니다.

CPU 강제 제한으로 프로세스가 종료되면 JSON 없이 일반 unavailable 오류가 날 수 있습니다. 영수증을 먼저 확인합니다.

서버 관리자는 승인된 공개키에만 아래 형태의 한정 접근을 추가합니다. principal은 키마다 고유하게 지정합니다.

```text
restrict,expiry-time="<7일 이내 UTC>",command="/usr/bin/python3 /opt/app/monitoring/ai-notes/receive.py --principal <고유별칭>" ssh-ed25519 <공개키>
```

실제 보안 경계는 authorized_keys의 restrict/command/만료입니다. SSH_ORIGINAL_COMMAND marker 검사는 보조 장치입니다.
다른 명령·대화형 셸·scp/sftp·포워딩이 거부되는지 실제 키로 확인한 뒤 활성화합니다. 기존 관리자 키를 MCP에 주지 않습니다.

PC는 개인키를 전용 macOS Keychain에 base64로 보관합니다. 연결 중에만 /tmp의 0700 폴더/0600 파일로 쓰고 정상 종료·timeout·SIGTERM/SIGHUP 때 지웁니다.
SIGKILL/전원 종료는 즉시 정리를 보장하지 못하므로 서버의 7일 이내 만료를 필수로 둡니다.
MCP 설정은 host/deploy user/Keychain 참조/검증된 known_hosts 경로만 담습니다. 예시는 ai-notes/connection.example.json입니다.
`mcp_server.py --config <설정 JSON>`을 stdio로 실행합니다. macOS 외 자격증명 transport는 별도 구현하되 분석 계약은 유지합니다.
해제: PC MCP 비활성화 → 해당 공개키 한 줄 제거 → 전용 Keychain 항목 폐기. 다른 키/데이터는 삭제하지 않습니다.

## 주문 MCP 함께 쓰기(#1041)

AI 클라이언트에 MCP 두 개를 함께 등록합니다. ai-notes(지표 문맥·게시, SSH stdio, `ai-notes/connection.example.json`)와 주문 운영 조회(HTTP, Codex `config.toml` 형식 `ai-notes/order-mcp.example.toml`)입니다.
주문 MCP는 Gateway 관리자 경로 `/api/v1/admin/orders/mcp`의 읽기 전용 Streamable HTTP(세션 없음)이며 dev에서만 켭니다(`.env.dev`의 `ORDER_MCP_ENABLED=true`). 조작 도구는 없습니다.

- 인증: 관리자 로그인(`POST /api/v1/auth/admin/login`)으로 받은 access 토큰을 환경변수 `KIDLY_ADMIN_TOKEN`에 두고 Codex가 `bearer_token_env_var`로 읽습니다. 설정 파일·저장소·채팅에 토큰 값을 쓰지 않습니다
- 토큰은 관리자 전체 권한입니다. 셸 프로필(`~/.zshrc` 등)에 두지 말고 시연·분석하는 터미널 세션에서만 설정하고, 끝나면 `unset KIDLY_ADMIN_TOKEN`으로 지웁니다
- 만료: access 토큰은 30분 뒤 만료됩니다(회원 서비스 `auth.yml`의 `access-token-validity`). 만료되면 주문 MCP가 401을 돌려줍니다. 다시 로그인해 환경변수만 바꿉니다. Gateway 관리자 검사는 그대로입니다
- 해제: AI 클라이언트에서 주문 MCP 항목을 지우고 환경변수를 지웁니다. dev 스위치를 끄면(`ORDER_MCP_ENABLED=false`) 경로가 없어집니다(404)

분석 순서:

1. `get_analysis_context(environment)`로 지표 문맥과 deadline을 받습니다(300초 안에 게시)
2. 주문 MCP 도구로 운영 상태를 조회합니다: `stuck_orders_summary` → 건수가 있는 종류만 `stuck_orders(kind)` → 필요하면 `order_timeline(orderId)`. 수신 실패는 `dead_letters` · `dead_letter_detail(deadLetterId)`, 보내기 실패는 `outbox_quarantined`, 그 밖에 `event_failures` · `payment_recoveries`
3. 조회 결과를 `AnalysisReport` 근거에 넣습니다. 도구 이름과 건수·종류·ID처럼 확인한 사실만 쓰고 원인 가설과 구분합니다. 개인정보는 주문 MCP 응답에 없지만 근거에도 넣지 않습니다
4. `publish_analysis(context_id, report)`로 게시하고 `get_publication_status`로 확인합니다

멈춘 주문 AI 진단 메모(#926, `ORDER_AI_ENABLED=true`, 키는 deploy 워크플로가 붙이는 `GROQ_API_KEY`)는 주문 서비스가 따로 남깁니다. 검증되지 않은 모델 문구라 주문 MCP는 `aiMemo`를 내보내지 않습니다(null). 사람이 관리자 멈춘 주문 API로 확인만 하고 메모 본문을 분석 근거에 옮기지 않습니다. 근거에는 "메모 있음 · confidence" 같은 사실만 씁니다.
`GROQ_API_KEY`는 커뮤니티 리뷰 검사(#1002)와 Groq 무료 시험도 같이 씁니다. 키를 바꾸거나 폐기하면 커뮤니티 · 주문 · 시험 모두 영향을 받습니다(무료 한도는 모델별).
서버에서 compose 값을 확인할 때는 `docker compose ... config -q`로 문법만 봅니다(`config`는 키 값을 화면에 찍습니다). 시연 순서는 [주문 AI 시연 대본](order-ai-demo.md)을 봅니다.

## 데이터·오류

- localhost Prometheus 고정 쿼리만 허용하며 env/서비스명과 집계 숫자만 발급합니다. 원본 라벨·업무 로그·식별값은 읽지 않습니다
- 발급 문맥은 원본 관측과 principal에 묶습니다. deadline은 발급 시각+300초의 절대 Unix 초입니다
- 보고서의 URL/이메일/사설 IP/일부 토큰·키 패턴과 제어 문자를 거부합니다. 모든 개인정보/비밀값을 탐지한다는 보장은 없습니다
- 입력 50KB/메모 100KB, receiver 전체 30초·CPU 10초·가상주소 공간 384MiB. 실서버 import 시 예약 주소공간 약 240MiB/RSS 약 20MiB를 확인해 주소공간 제한을 정했습니다. HTTP timeout/프록시·리다이렉트 거부
- 발급 30초 간격, 미사용 문맥 1시간 보관, principal별 1,000개/전체 2,000개 상한. 사용 완료 기록 14일, 미전송 outbox는 자동 삭제하지 않음
- 고정 오류 코드: rate_limited/capacity_reached/conflict/not_found/forbidden/deadline_exceeded/request_timeout/collection_unavailable/delivery_unavailable/storage_unavailable/invalid_request/unavailable. 원문 응답·입력을 오류에 넣지 않음

SQLite는 `/opt/app/monitoring/ai-notes-state/notes.sqlite3`(0600, 디렉터리 0700)입니다.
전송 전에 outbox를 보존하고 같은 context/digest는 같은 영수증, 다른 내용은 충돌입니다. 재시도는 저장한 동일 timestamp/본문을 사용합니다.
Loki는 고정 job=kidly-ai-notes/env 라벨과 JSON 메모 본문만 사용합니다(기존 14일 보관). 새 포트/인증 권한/외부 저장소는 없습니다.
기존 Loki는 수집망 내부 신뢰 방식입니다. 그 망의 다른 쓰기 주체가 같은 stream을 쓰는 위조를 이 화면만으로 판별하지 못합니다.
Alloy textfile에는 마지막 관측·미전송 건수·문맥 수만 기록하며 분석 문구는 지표 라벨에 넣지 않습니다.

## 만료된 미전송 메모

AI/MCP 경로는 만료 후 새 게시를 허용하지 않습니다. 운영자는 클라이언트 제출을 멈춘 뒤 다음 명령으로 **이미 접수된 결과만** 처리합니다.

```sh
sudo -u deploy python3 /opt/app/monitoring/ai-notes/maintenance.py list-pending
sudo -u deploy python3 /opt/app/monitoring/ai-notes/maintenance.py retry-stored --context-id <ID>
sudo -u deploy python3 /opt/app/monitoring/ai-notes/maintenance.py discard-pending --context-id <ID>
```

retry-stored는 저장된 결과를 고정 Loki로 전송하고 AI를 부르지 않습니다. 기본 설정상 7일 이상 지난 timestamp는 Loki가 거부할 수 있으므로 반복 전송 대신 discard-pending으로 닫습니다.
discard-pending은 미전송 표시를 닫되 본문을 즉시 삭제하지 않고 14일 보관합니다. 전송 완료로 속이지 않습니다. receiver/MCP에는 이 운영 명령이 노출되지 않습니다.
