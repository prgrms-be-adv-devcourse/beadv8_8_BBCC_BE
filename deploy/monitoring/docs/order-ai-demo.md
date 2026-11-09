# 주문 AI 시연 대본(dev)

주문 운영 조회 MCP(#925)와 멈춘 주문 AI 진단 메모(#926)를 dev에서 한 번에 보여 준다(#1041). 실행은 사용자가 한다.
자격증명 · 토큰 · 키 값은 이 문서 · 저장소 · 채팅에 쓰지 않는다. 토큰은 시연하는 터미널 세션의 환경변수에만 두고 끝나면 지운다
(관리자 `KIDLY_ADMIN_TOKEN`, 구매자 `KIDLY_BUYER_TOKEN`. 관리자 토큰은 전체 권한 · 30분 만료).

## 준비

- dev 배포에 `.env.dev`의 `ORDER_MCP_ENABLED=true` · `ORDER_AI_ENABLED=true`가 들어가 있고, dev Environment Secret `GROQ_API_KEY`가 등록돼 있다(배포 워크플로가 서버 `.env`에 붙이고 compose가 `ORDER_AI_API_KEY`로 넘긴다). 서버에서 확인할 때는 `config -q`로 문법만 본다(키 값이 찍히지 않게)
- Codex에 MCP 두 개를 등록했다: ai-notes(`ai-notes/connection.example.json`) · 주문(`ai-notes/order-mcp.example.toml`, URL의 호스트를 dev 주소로)
- 관리자 토큰: `POST /api/v1/auth/admin/login`. 구매자 토큰: dev 회원 로그인. 만료되면 다시 로그인한다
- 주문 요청(`POST /api/v1/orders`, 구매자 토큰) 필수값: `items[]`마다 `optionId` · `quantity` · `unitPrice`(상품 상세 `GET /api/v1/products/{productId}`의 지금 가격. 다르면 409), `shippingAddress`의 `recipientName` · `phone` · `zipCode` · `address`. 배송지는 형식만 맞춘 시연용 값을 쓴다(예: `"홍길동"` · `"010-0000-0000"` · `"00000"` · `"시연시 시연로 1"`, 실제 개인정보 금지)

## 멈춘 주문을 만드는 방법

재고 차감 이벤트가 오지 않는 상황을 만든다: 상품 서비스를 잠깐 멈추고 주문하면 주문이 결제 대기(`PENDING_PAYMENT`)에서 멈추고, 5분(`kidly.order.stuck.stock-wait`) 뒤 `STOCK_CHECK_STALE`로 보인다.

시간이 빠듯하다:
- 결제 마감은 10분(`kidly.order.payment-timeout`)이고 만료 작업이 1분마다 돌아 주문 뒤 10~11분에 취소된다. 취소되면 멈춘 주문 목록에서 빠져 `aiMemo`를 못 본다
- AI 진단은 앞 실행이 끝난 뒤 5분(`kidly.order.ai.interval`)마다 돌고 한 번에 최대 약 2분 반이 걸릴 수 있다. 그래서 멈춤이 보이는 5~10분 사이에 진단이 끝나지 못할 수 있다

대안 1(권장): 시연 동안만 결제 마감을 늘린다. dev 서버 `/opt/app/dev`의 `.env` 끝에 `ORDER_PAYMENT_TIMEOUT=PT20M` 한 줄을 넣고 주문 서비스만 다시 띄운다. 끝나면 그 줄을 지우고 다시 띄운다(기본 PT10M으로 돌아감). 이때 4단계 · 5단계의 'T0 + 10분'은 'T0 + 20분', 9단계의 '11분'은 '21분'으로 읽는다.
조르기 만료 유예(#1008, 기본 PT15M)는 결제 마감보다 길어야 해서, PT20M으로 늘린 동안에는 기동 때 유예가 짧다는 WARN이 한 줄 난다. 기한 전에 만든 조르기 주문의 결제가 늦게 오는 경우에만 영향이 있고 시연 동안만이다.

```sh
cd /opt/app/dev
echo 'ORDER_PAYMENT_TIMEOUT=PT20M' >> .env
docker compose -p dev --env-file .env --env-file image.env up -d order-service
# 시연이 끝나면
sed -i '/^ORDER_PAYMENT_TIMEOUT=/d' .env
docker compose -p dev --env-file .env --env-file image.env up -d order-service
```

대안 2: **놓치면 새 주문으로 다시 한다**(상품 서비스를 멈춘 채 2단계부터). 반복할 때는 바로 앞 진단이 끝난 직후에 주문하면 5~10분 사이에 다음 진단이 온다.

상품 서비스를 멈추는 동안 다른 팀원의 상품 · 재고 처리가 늦어지고, 5분이 넘으면 Grafana '서비스 다운' 경보(for 5m)가 Slack으로 나간다. 시작 전에 팀에 알리고 짧게 한다.

상품 서비스 멈춤 · 다시 시작은 dev 서버의 `/opt/app/dev`에서 배포와 같은 방식으로 한다:

```sh
cd /opt/app/dev
docker compose -p dev --env-file .env --env-file image.env stop product-service
docker compose -p dev --env-file .env --env-file image.env start product-service
```

## 순서

| 단계 | 할 일 | 확인 |
|---|---|---|
| 0 | 관리자 토큰으로 `GET /api/v1/admin/orders/stuck/summary` | 200, 종류별 건수(시작 값 기록) |
| 0 | Codex에서 주문 MCP 도구 목록 보기 | `stuck_orders_summary` · `stuck_orders` · `order_timeline` · `dead_letters` · `dead_letter_detail` · `event_failures` · `payment_recoveries` · `outbox_quarantined` |
| 1 | 팀에 알린 뒤 dev 서버에서 상품 서비스 `stop` | 상품 서비스 컨테이너 멈춤 |
| 2 | 구매자 토큰으로 `POST /api/v1/orders`(상품 1개, 시연용 배송지) → 시각 T0 | 201, 상태 `PENDING_PAYMENT`, 주문 ID 기록 |
| 3 | T0 + 5분 뒤 `GET /api/v1/admin/orders/stuck/summary` · `GET /api/v1/admin/orders/stuck?kind=STOCK_CHECK_STALE` | `STOCK_CHECK_STALE` 1 이상, 목록에 그 주문 ID |
| 4 | T0 + 10분 전까지 3의 목록을 1분마다 다시 조회 | 그 줄의 `aiMemo`가 null이 아님: `summary` · `recommendedAction` · `confidence` · `provider`(`api.groq.com`) · `model` · `createdAt`. T0 + 10분까지 null이면 새 주문으로 2단계부터 다시 |
| 5 | Codex: `get_analysis_context(dev)` → 주문 MCP `stuck_orders_summary` → `stuck_orders(kind=STOCK_CHECK_STALE)` → `order_timeline(orderId)` (T0 + 10분 전에) | 문맥 발급(deadline), 도구 응답에 그 주문(개인정보 칸 없음, `aiMemo`는 MCP에서 늘 null) |
| 6 | Codex가 근거(지표 + 주문 MCP 조회 결과)를 넣어 `publish_analysis` → `get_publication_status`. `aiMemo` 본문은 근거에 옮기지 않고 "진단 메모 있음 · confidence"만 쓴다 | 영수증 · 전송 완료 |
| 7 | dev 서버에서 상품 서비스 `start`(멈춘 지 5분이 넘었으면 서비스 다운 경보 해제 확인) | 상품 서비스가 밀린 주문 생성 이벤트를 처리. 결제하지 않은 주문은 결제 마감 뒤 만료 취소되고 재고가 돌아온다 |
| 8 | Grafana → Kidly → Kidly AI 운영 분석 | 방금 메모 행, 근거에 주문 MCP 도구 이름 · 종류 · 건수 |
| 9 | 마지막 주문의 T0 + 11분 뒤 `GET /api/v1/admin/orders/stuck/summary` | 시작 값으로 돌아옴(시연 주문은 모두 만료 취소) |
| 10 | 터미널에서 `unset KIDLY_ADMIN_TOKEN KIDLY_BUYER_TOKEN` | 토큰 환경변수 지움 |

## 안 될 때

- 주문 MCP 401: 관리자 토큰 만료(30분) · 누락. 다시 로그인한다. 404: dev 스위치가 꺼져 있다(`ORDER_MCP_ENABLED`)
- `aiMemo`가 계속 null: `ORDER_AI_ENABLED` · 키(`GROQ_API_KEY`)가 없으면 진단이 꺼진다. 하루 상한(기본 200, 커뮤니티 리뷰 검사 · 팀 Groq 시험과 같은 키, 한도는 모델별)이나 서킷브레이커 열림(10분)일 수 있다. 진단 주기를 놓쳤으면 새 주문으로 다시 한다
- 3단계에 안 보임: 상품 서비스가 실제로 멈췄는지, T0에서 5분이 지났는지 본다

## 기록

PR에 단계별 호출 · 응답(토큰 · 키를 지운 상태 코드와 주요 칸) · Grafana 화면을 남긴다.
