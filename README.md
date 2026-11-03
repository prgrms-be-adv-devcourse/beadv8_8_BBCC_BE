# Kidly BE

자녀 맞춤 키즈 패션 이커머스 백엔드. Spring Boot 4.1 · Java 25 · PostgreSQL 18 · Kafka 모듈러 모놀리스.

개발 규칙은 [`CLAUDE.md`](CLAUDE.md)를 따른다.

## 준비

- JDK 25, Docker Compose를 쓸 수 있는 Docker 환경 (Gradle은 Wrapper를 쓴다)

## 로컬 실행

1. `.env.example`을 루트에 `.env`로 복사한다. 로컬은 기본값이 있어 비워 둬도 된다(앱이 실행될 때 루트의 `.env`를 자동으로 읽는다)
2. `docker compose up -d` — PostgreSQL · Kafka · pgAdmin · Prometheus · Grafana
3. `./gradlew :apps:kidly-app:bootRun` — 앱(8080). 기본 프로필 local. 루트에서 실행한다(IDE는 공유 실행 구성 `KidlyApplication`)
4. `./gradlew :apps:gateway:bootRun` — Gateway(8083). 루트에서 실행한다(IDE는 `GatewayApplication`, 3 · 4를 한 번에 `Kidly 앱 + Gateway`)
5. `./gradlew :apps:member-service:bootRun` — 회원 서비스(8081). 회원 · 인증 · 판매자 API(로그인 포함)는 앱에 없어 이 서비스를 띄워야 한다(Gateway가 8081로 넘긴다). 앱(3)이 한 번 뜬 뒤에 띄운다(빈 DB에서 먼저 뜨면 앱의 마이그레이션이 실패한다). IDE는 `MemberServiceApplication`
6. `./gradlew :apps:childprofile-service:bootRun` — 자녀 프로필 서비스(8084). 자녀 API는 앱에 없어 이 서비스를 띄워야 한다(Gateway가 8084로 넘긴다). 앱(3)이 한 번 뜬 뒤에 띄운다(빈 DB에서 먼저 뜨면 앱의 마이그레이션이 실패한다). IDE는 `ChildProfileServiceApplication`. 같은 이유로 `Kidly 앱 + Gateway`에는 넣지 않았다
7. `./gradlew :apps:recommend-service:bootRun` — 추천 서비스(8086). 추천 API는 앱에 없어 이 서비스를 띄워야 한다(Gateway가 8086으로 넘긴다). 앱(3)이 한 번 뜬 뒤에 띄운다(빈 DB에서 먼저 뜨면 앱의 마이그레이션이 실패한다). IDE는 `RecommendServiceApplication`
8. (선택) `./gradlew :apps:payment-service:bootRun` — 결제 서비스(8087). 앱(3)이 한 번 뜬 뒤에 띄운다(빈 DB에서 먼저 뜨면 앱의 마이그레이션이 실패한다). 앱도 아직 결제 API를 받는다. Gateway를 `KIDLY_PAYMENT_URI=http://localhost:8087`로 띄우면 결제 API가 이리로 간다
9. (선택) `./gradlew :apps:settlement-service:bootRun` — 정산 서비스(8088). 앱(3)이 한 번 뜬 뒤에 띄운다(빈 DB에서 먼저 뜨면 앱의 마이그레이션이 실패한다). 앱도 아직 정산 API를 받는다. Gateway를 `KIDLY_SETTLEMENT_URI=http://localhost:8088`로 띄우면 정산 API가 이리로 간다

- pgAdmin: http://localhost:5050 — 로그인 `admin@kidly.dev` / `kidly`, 등록된 PostgreSQL 서버 접속 비밀번호 `kidly` (로컬 개발용)
- 프론트와 Swagger(http://localhost:8083/swagger-ui.html)는 Gateway 주소(8083)로 붙는다. Swagger 위쪽에서 앱 문서(`kidly`)와 `회원` · `자녀 프로필` · `추천` 문서를 고른다(회원 문서는 5, 자녀 문서는 6, 추천 문서는 7이 떠 있어야 열린다). 앱(8080)에 직접 붙으면 Gateway가 붙이는 헤더가 없어 로그인이 필요한 API는 401이고, 앱에는 CORS 설정이 없어 브라우저(프론트 5173)에서는 공개 API도 막힌다
- Gateway는 Kafka(`kidly.auth.events`)로 토큰 무효화를 받는다. 회원 서비스보다 먼저 떠도 기동은 되지만, 회원 서비스가 한 번 떠서 토픽이 만들어진 뒤에 무효화 목록이 채워진다
- 회원 · 판매자 토픽(`kidly.member.events` · `kidly.seller.events`)은 회원 서비스가 처음 뜰 때 만든다. 새 Kafka에서는 자녀 토픽과 같은 이유로 그 전에 떠 있던 앱의 리스너(알림 · 결제 · 상품 · 주문)가 그 사이에 나간 메시지(회원 서비스가 뜨며 넣는 초기 회원 · 판매자 데이터 등)를 건너뛴다. 처음 한 번은 회원 서비스를 띄운 뒤 앱을 다시 시작한다
- 자녀 토픽(`kidly.childprofile.events`)은 자녀 프로필 서비스가 처음 뜰 때 만든다. 새 Kafka(빈 `kafka-data` 볼륨)에서는 그 전에 떠 있던 앱의 주문 리스너와 추천 서비스의 리스너가 몇 초 뒤 메타데이터를 갱신하고서야 토픽을 받고, 책갈피가 없어 그때의 끝(`auto-offset-reset: latest`)부터 읽는다. 그 사이에 나간 자녀 메시지(서비스가 뜨며 넣는 초기 자녀 데이터 등)는 건너뛰고 다시 오지 않는다. 처음 한 번은 자녀 프로필 서비스를 띄운 뒤 앱과 추천 서비스를 다시 시작한다
- 검색 토픽(`kidly.search.events`)은 검색 서비스가 처음 뜰 때 만든다. 로컬 실행 순서와 prod에는 검색 서비스가 없어 토픽이 없으므로, 추천 서비스는 기동은 되지만 검색 리스너가 토픽을 찾는 `UNKNOWN_TOPIC_OR_PARTITION` WARN을 1초에 한두 줄씩 남긴다. Elasticsearch(`docker compose --profile search up -d`)와 검색 서비스(`./gradlew :apps:search-service:bootRun`)를 한 번 띄워 토픽이 생기면 사라진다
- 추천 서비스는 앱이 한 번 뜬 뒤에 띄우므로, 새 Kafka에서는 앱이 처음 뜨며 넣은 초기 판매자 · 상품 데이터의 메시지가 추천 서비스의 구독 그룹(`kidly.recommend`) 책갈피가 생기기 전에 나가 추천 복사본에 들어오지 않는다. 그 뒤 판매자 · 상품을 바꾸거나 새로 만들면 그때부터 들어온다(읽기 시작 위치는 팀 규칙대로 `latest`다)

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
| `apps/kidly-app` | 실행 앱. 앱 설정, 공통 테스트 기반(`support`, `TestcontainersConfiguration`) |
| `apps/gateway` | API Gateway(WebFlux). 토큰 검증 · 무효화 확인 · 역할 검사 · CORS · 라우팅 |
| `apps/member-service` | 회원 서비스. 회원 · 인증 · 판매자 도메인은 이 서비스에만 있다. 빈 DB에서는 앱이 먼저 회원 테이블을 만들고, 서비스는 그 버전에서 baseline을 잡는다 |
| `apps/childprofile-service` | 자녀 프로필 서비스(#682). 자녀 프로필 도메인은 이 서비스에만 있다 |
| `apps/recommend-service` | 추천 서비스(#704). 추천 도메인은 이 서비스에만 있다 |
| `apps/payment-service` | 결제 서비스. 앱도 아직 결제 도메인을 가지고 같은 구독 그룹으로 함께 돈다(#525). 그동안 결제 마이그레이션을 더하면 서비스의 `baseline-version`을 올린다 |
| `apps/settlement-service` | 정산 서비스. 앱도 아직 정산 도메인을 가지고 같은 구독 그룹(`kidly.settlement`)으로 함께 돈다(#729). 자동 정산은 Batch JobInstance, 아웃박스 릴레이는 advisory lock으로 한 번만 돈다. 그동안(다음 PR에서 앱의 정산을 빼기 전) 정산 마이그레이션은 앱과 서비스가 각자 적용하므로, 더해야 하면 두 번 적용돼도 안전하게(`IF NOT EXISTS`) 쓴다. 서비스 baseline은 처음 기동 때 한 번만 잡혀 올려도 소용없다. 서비스에는 주문 · 결제가 없어 정산 Kafka 수신 스위치(`SETTLEMENT_KAFKA_CONSUMER_ENABLED`)를 끄면 이벤트를 받지 못한다 |
| `common` | 공통 코드(`global`): 이벤트 발행, 메일, 엔티티 기반 클래스, 오류 · 응답 형식 |
| `shared` | 도메인 사이 계약: 이벤트(`DomainEvent`), 도메인별 `event` · `dto` · `domain` · `out` |
| `domains/*` | 도메인 모듈. 폴더 이름이 도메인 이름이다 |
| `architecture` | 도메인 모듈 안의 계층 규칙(ArchUnit `LayerRules`) |

## 인증

토큰 검증 · 무효화 확인 · 역할 검사 · CORS는 Gateway만 한다. 서비스는 Spring Security 없이 Gateway가 붙인 헤더로 로그인 회원을 안다.

- Gateway는 밖에서 온 `X-Member-*` · `X-Admin-*` · `X-Child-*`를 지우고, 검증을 통과한 회원 토큰이면 `X-Member-Id` · `X-Member-Roles` · `X-Member-Nickname`(UTF-8 퍼센트 인코딩)을, 관리자 토큰이면 `X-Admin-Id`만, 자녀 토큰이면 `X-Child-Account-Id` · `X-Child-Guardian-Id`만 붙인다. 계약은 `shared`의 `com.bbcc.kidly.shared.auth`(토큰 주체 `PrincipalId` · 주체 헤더 `PrincipalHeaders` · 회원 헤더 `MemberHeaders` · 역할 `Role` · 토큰 클레임 이름 `AccessTokenClaims`)다
- 회원 로그인이 필요한 API는 `@LoginMember AuthMember`(회원 ID · 닉네임)를, 관리자 로그인이 필요한 API는 `@LoginAdmin AuthAdmin`(관리자 ID)을, 자녀 전용 API는 `@LoginChild AuthChild`(자녀 계정 ID · 보호자 회원 ID)를 받는다. 자녀 토큰에는 `X-Member-Id`가 없어 회원 API는 401이다. 헤더가 없거나 잘못되면 401이다. 그 밖의 API는 공개다
- `/api/v1/admin/**`는 관리자 주체, `/api/v1/seller/**`는 SELLER 역할의 회원 주체만, `/api/v1/child/**`는 자녀 주체만 Gateway가 통과시킨다(토큰이 없으면 401, 주체 또는 역할이 다르면 403). 서비스는 역할 경로를 검사하지 않는다
- Gateway는 보안 응답 헤더를 붙이고, 정규화되지 않은 경로 · 위험한 요청은 400으로 막는다

서비스는 Gateway 뒤에서만 열린다는 전제다(dev 8083 · prod 8084 · 로컬 8083). Gateway를 거치지 않고 서비스에 닿을 수 있으면 `X-Member-*` 헤더를 위조할 수 있다. 자세한 Gateway 동작은 [`apps/gateway/CLAUDE.md`](apps/gateway/CLAUDE.md)

요청 로그(`RequestLoggingFilter`)의 요청 헤더에 `x-member-nickname`(퍼센트 인코딩된 닉네임)이 남는다. 로그는 이름 · ID를 남기는 정책이라(`LogMasker`) 가리지 않는다.
