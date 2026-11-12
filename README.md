# Kidly BE

자녀 맞춤 키즈 패션 이커머스 백엔드. Spring Boot 4.1 · Java 25 · PostgreSQL 18 · Kafka. API Gateway 뒤에 도메인별 서비스가 따로 뜨는 모노레포다.

개발 규칙은 [`CLAUDE.md`](CLAUDE.md)를 따른다.

팀 문서(기획서 · Kafka 규칙 · API 목록): [문서 사이트](https://prgrms-be-adv-devcourse.github.io/beadv8_8_BBCC_BE/) · [위키](https://github.com/prgrms-be-adv-devcourse/beadv8_8_BBCC_BE/wiki). 원본은 [`docs/`](docs/README.md)이고 develop에 머지되면 둘 다 자동으로 바뀐다.

## 준비

- JDK 25, Docker Compose를 쓸 수 있는 Docker 환경 (Gradle은 Wrapper를 쓴다)

## 로컬 실행

1. `.env.example`을 루트에 `.env`로 복사한다. 로컬은 기본값이 있어 비워 둬도 된다(서비스가 실행될 때 루트의 `.env`를 자동으로 읽는다)
2. `docker compose up -d` — PostgreSQL · Kafka · Redis(추천의 AI 답 저장소) · pgAdmin · Prometheus · Grafana
3. `./gradlew :apps:gateway:bootRun` — Gateway(8083). 루트에서 실행한다. 모든 API 요청은 Gateway로 보낸다. 라우트에 없는 경로는 404다
4. 필요한 서비스를 띄운다. Gateway가 경로별로 넘기므로 쓰려는 API의 서비스만 띄우면 된다. 서비스마다 자기 스키마를 직접 만들므로 빈 DB에서도 띄우는 순서는 상관없다. 루트에서 실행하고, IDE는 서비스마다 `XxxServiceApplication`, Gateway와 서비스를 한 번에 띄우려면 `Gateway + 서비스`를 쓴다

| 서비스 | 명령 | 포트 | API |
|---|---|---|---|
| 회원 | `cd apps/member-service && ./gradlew bootRun` | 8081 | 회원 · 인증(로그인) · 판매자 · 자녀. 루트 빌드와 따로 자체 wrapper로 빌드한다([구조와 실행](apps/member-service/README.md)) |
| 검색 | `docker compose --profile search up -d` 뒤 `./gradlew :apps:search-service:bootRun` | 8082 | 상품 목록(`GET /api/v1/products`). 색인은 처음 뜬 뒤 판매자 · 상품을 바꾸거나 관리자 다시 보내기로 채운다 |
| 추천 | `./gradlew :apps:recommend-service:bootRun` | 8086 | 추천 |
| 결제 | `./gradlew :apps:payment-service:bootRun` | 8087 | 결제 · 지갑 |
| 정산 | `./gradlew :apps:settlement-service:bootRun` | 8088 | 판매자 정산서 · 정산 대상, 관리자 정산 실행 · 조회 · 지급 재요청 · 실패 메시지(DLT) |
| 상품 | `./gradlew :apps:product-service:bootRun` | 8089 | 상품(목록 제외) · 카테고리 |
| 알림 | `./gradlew :apps:notification-service:bootRun` | 8090 | 회원 · 자녀 알림함, 웹 푸시 등록, 관리자 실패 메시지(DLT). 메일 · 푸시는 이벤트를 받아 보낸다 |
| 주문 | `./gradlew :apps:order-service:bootRun` | 8091 | 주문 · 장바구니 · 배송 · 조르기 |
| 커뮤니티 | `./gradlew :apps:community-service:bootRun` | 8092 | 리뷰 · 찜 |
| 챗봇 | `./gradlew :apps:chatbot-service:bootRun` | 8093 | 챗봇 질문(`POST /api/v1/chatbot/messages`) · 관리자 답변 근거 문서. 정책 · FAQ · 사이즈는 등록한 문서로, 상품 찾기는 검색 서비스(8082)를 불러 답한다. OpenAI 키(`OPENAI_API_KEY`)가 없으면 503 |

- pgAdmin: http://localhost:5050 — 로그인 `admin@kidly.dev` / `kidly`, 등록된 PostgreSQL 서버 접속 비밀번호 `kidly` (로컬 개발용)
- 프론트는 Gateway 주소(8083)로 붙는다. Swagger는 서비스마다 따로 연다(`http://localhost:<서비스 포트>/swagger-ui.html`, 알림 서비스는 없다). 서비스에 직접 붙으면 Gateway가 붙이는 헤더가 없어 로그인이 필요한 API는 401이고, 서비스에는 CORS 설정이 없어 브라우저(프론트 5173)에서는 공개 API도 막힌다
- Gateway는 Kafka(`kidly.auth.events`)로 토큰 무효화를 받는다. 회원 서비스보다 먼저 떠도 기동은 되지만, 회원 서비스가 한 번 떠서 토픽이 만들어진 뒤에 무효화 목록이 채워진다
- 토픽은 보내는 서비스가 처음 뜰 때 만든다(회원 · 판매자 · 자녀 · 인증은 회원 서비스, 상품은 상품 서비스, 검색은 검색 서비스, 리뷰 · 찜은 커뮤니티 서비스). 받는 서비스는 책갈피가 없으면 그때의 끝(`auto-offset-reset: latest`)부터 읽으므로, 새 Kafka(빈 `kafka-data` 볼륨)에서 받는 서비스의 구독 그룹이 생기기 전에 나간 메시지(보내는 서비스가 뜨며 넣는 초기 회원 · 판매자 · 자녀 · 상품 데이터 등)는 건너뛰고 다시 오지 않는다. 처음 한 번은 보내는 서비스(회원 · 상품)를 띄운 뒤 받는 서비스(주문 · 결제 · 추천 · 커뮤니티 · 정산 · 알림)를 다시 시작한다. 상품은 관리자 `POST /api/v1/admin/products/changed-events`로 판매 중인 상품의 `product.changed`를 다시 보낼 수도 있다
- 검색 서비스를 띄우기 전에는 검색 토픽(`kidly.search.events`)이 없으므로, 추천 서비스는 기동은 되지만 검색 리스너가 토픽을 찾는 `UNKNOWN_TOPIC_OR_PARTITION` WARN을 1초에 한두 줄씩 남긴다. 검색 서비스를 한 번 띄워 토픽이 생기면 사라진다. 커뮤니티 서비스를 띄우기 전의 커뮤니티 토픽(`kidly.community.events`)도 같다

## 빌드 · 테스트

| 할 일 | 명령 | Docker |
|---|---|---|
| 전체(Checkstyle · ArchUnit · 모든 테스트) | `./gradlew build` | 필요 |
| 경계 규칙만 | `./gradlew archTest` | 불필요 |
| 단위 테스트(`XxxTest`) | `./gradlew test` | 불필요 |
| 통합 테스트(`XxxIntegrationTest`, Kafka 통합 테스트 포함) | `./gradlew integrationTest` | 필요 |
| 기능 테스트(`XxxApiTest`) | `./gradlew functionalTest` | 필요 |

## 모듈

| 모듈 | 내용 |
|---|---|
| `apps/gateway` | API Gateway(WebFlux). 토큰 검증 · 무효화 확인 · 역할 검사 · CORS · 라우팅 |
| `apps/member-service` | 회원 서비스. 회원 · 인증 · 판매자 도메인은 이 서비스에만 있다. |
| `apps/recommend-service` | 추천 서비스(#704). 추천 도메인은 이 서비스에만 있다 |
| `apps/payment-service` | 결제 서비스(#525). 결제 도메인은 이 서비스에만 있다. 결제 마이그레이션은 서비스 `baseline-version`(`202611021930`, 고정)보다 뒤 버전으로 더한다 |
| `apps/settlement-service` | 정산 서비스(#729). 정산 도메인은 이 서비스에만 있다. 구매확정 · 지급 결과는 Kafka로만 받으므로 수신 스위치(`SETTLEMENT_KAFKA_CONSUMER_ENABLED`)를 끄면 이벤트를 받지 못한다. k3s CronJob `settlement-daily-run`이 매일 10시에 한 번 자동 정산하고(공휴일은 공공데이터 API `HOLIDAY_API_SERVICE_KEY`로 받아 저장한 목록으로 건너뛴다), 영업일 12:30까지 그날 실행이 끝나지 않으면 `settlement_run_overdue` 게이지로 Grafana가 알린다(`SETTLEMENT_RUN_DEADLINE`). 지급 요청은 아웃박스에 남긴 뒤 `kidly.settlement.events`로 보낸다. 받기 실패 메시지는 DB에 보관해 관리자가 재처리한다 |
| `apps/product-service` | 상품 서비스(#791). 상품 도메인은 이 서비스에만 있다. 상품 마이그레이션(`domains/product`의 `db/migration/product`)은 이 서비스만 적용한다. 서비스 baseline은 처음 기동 때 한 번만 잡혀 올려도 소용없다 |
| `apps/order-service` | 주문 서비스(주문 · 장바구니 · 배송 · 조르기, #826). 주문 도메인은 이 서비스에만 있다. 주문 마이그레이션은 이 서비스만 적용한다. 주문 표가 이미 있는 DB는 baseline(`202611031100`, 고정)으로 건너뛰고, 빠진 표는 보강 마이그레이션이 만든다 |
| `apps/community-service` | 커뮤니티 서비스(리뷰 · 찜, #827). 커뮤니티 도메인은 이 서비스에만 있다. 마이그레이션은 도메인 모듈에만 있고 이 서비스만 적용한다. 이미 테이블이 있는 DB는 baseline(`202611031200`, 고정)으로 건너뛴다 |
| `apps/chatbot-service` | 챗봇 서비스(#1191). 챗봇 도메인은 이 서비스에만 있다. 마이그레이션은 도메인 모듈(`db/migration/chatbot`)에만 있고 이 서비스만 적용한다. Kafka를 쓰지 않는다 |
| `apps/notification-service` | 알림 서비스(#833). 알림 도메인은 이 서비스에만 있다. 회원 · 판매자 · 상품 · 주문 · 결제 · 인증 토픽을 받아(구독 그룹 `kidly.notification`) 메일 · 알림함 · 웹 푸시(FCM, `FIREBASE_CREDENTIALS`가 비면 보내지 않음)를 남긴다. 받기 실패 메시지는 DB에 보관해 관리자가 재처리하고, 회원이 탈퇴하면 알림함 · 발송 주소 · 본문 · 푸시 FID를 바로 지운다. 알림함 API는 Gateway가 넘긴다. 알림 테이블이 이미 있는 DB는 `202610190901`(고정)에서 baseline을 잡는다. 새 알림 마이그레이션은 `domains/notification`의 `db/migration/notification`에 둔다 |
| `common` | 공통 코드(`global`): 이벤트 발행, 메일, 엔티티 기반 클래스, 오류 · 응답 형식. JPA 공통 동작은 `integrationTest`로 실제 PostgreSQL에서 확인한다 |
| `shared` | 도메인 사이 계약: 이벤트(`DomainEvent`), 도메인별 `event` · `dto` · `domain` · `out` |
| `domains/*` | 도메인 모듈. 폴더 이름이 도메인 이름이다 |
| `apps/member-service/{auth,member,seller,support}` | 회원 서비스 빌드의 Gradle 모듈. 회원 서비스는 자체 settings를 가진 별도 빌드다. [구조와 실행](apps/member-service/README.md) |
| `architecture` | 도메인 모듈마다 돌리는 ArchUnit 규칙(계층 `LayerRules`, 트랜잭션 격리 `TransactionRules`) |
| `docs` | 팀 공용 문서(기획서 · Kafka 규칙 · API 목록). develop에 머지되면 문서 사이트(GitHub Pages, VitePress)와 GitHub 위키에 자동으로 올라간다. Gradle 빌드와 따로, `docs/`에서 `npm ci` · `npm run dev`로 띄운다. [문서 고치는 법](docs/writing.md) |

## 인증

토큰 검증 · 무효화 확인 · 역할 검사 · CORS는 Gateway만 한다. 서비스는 Spring Security 없이 Gateway가 붙인 헤더로 로그인 회원을 안다.

- Gateway는 밖에서 온 `X-Member-*` · `X-Admin-*` · `X-Child-*`를 지우고, 검증을 통과한 회원 토큰이면 `X-Member-Id` · `X-Member-Roles` · `X-Member-Nickname`(UTF-8 퍼센트 인코딩)을, 관리자 토큰이면 `X-Admin-Id`만, 자녀 토큰이면 `X-Child-Account-Id` · `X-Child-Guardian-Id`만 붙인다. 계약은 `shared`의 `com.bbcc.kidly.shared.auth`(토큰 주체 `PrincipalId` · 주체 헤더 `PrincipalHeaders` · 회원 헤더 `MemberHeaders` · 역할 `Role` · 토큰 클레임 이름 `AccessTokenClaims`)다
- 회원 로그인이 필요한 API는 `@LoginMember AuthMember`(회원 ID · 닉네임)를, 관리자 로그인이 필요한 API는 `@LoginAdmin AuthAdmin`(관리자 ID)을, 자녀 전용 API는 `@LoginChild AuthChild`(자녀 계정 ID · 보호자 회원 ID)를 받는다. 자녀 토큰에는 `X-Member-Id`가 없어 회원 API는 401이다. 헤더가 없거나 잘못되면 401이다. 그 밖의 API는 공개다
- `/api/v1/admin/**`는 관리자 주체, `/api/v1/seller/**`는 SELLER 역할의 회원 주체만, `/api/v1/child/**`는 자녀 주체만 Gateway가 통과시킨다(토큰이 없으면 401, 주체 또는 역할이 다르면 403). 서비스는 역할 경로를 검사하지 않는다
- Gateway는 보안 응답 헤더를 붙이고, 정규화되지 않은 경로 · 위험한 요청은 400으로 막는다

서비스는 Gateway 뒤에서만 열린다는 전제다(dev 8083 · prod 8084 · 로컬 8083). Gateway를 거치지 않고 서비스에 닿을 수 있으면 `X-Member-*` 헤더를 위조할 수 있다. 자세한 Gateway 동작은 [`apps/gateway/CLAUDE.md`](apps/gateway/CLAUDE.md)

기본 요청 로그는 정규화 경로·상태·소요시간·traceId만 남기며, 요청·응답 본문과 헤더·IP·회원 식별값은 남기지 않는다. [요청 로그 정책](deploy/monitoring/docs/request-log-policy.md)을 따른다.
