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

- pgAdmin: http://localhost:5050 — 로그인 `admin@kidly.dev` / `kidly`, 등록된 PostgreSQL 서버 접속 비밀번호 `kidly` (로컬 개발용)
- 프론트와 Swagger(http://localhost:8083/swagger-ui.html)는 Gateway 주소(8083)로 붙는다. 앱(8080)에 직접 붙으면 Gateway가 붙이는 헤더가 없어 로그인이 필요한 API는 401이고, 앱에는 CORS 설정이 없어 브라우저(프론트 5173)에서는 공개 API도 막힌다
- Gateway는 Kafka(`kidly.member.events`)로 토큰 무효화를 받는다. 앱보다 먼저 떠도 기동은 되지만, 앱이 한 번 떠서 토픽이 만들어진 뒤에 무효화 목록이 채워진다

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
| `common` | 공통 코드(`global`): 이벤트 발행, 메일, 엔티티 기반 클래스, 오류 · 응답 형식 |
| `shared` | 도메인 사이 계약: 이벤트(`DomainEvent`), 도메인별 `event` · `dto` · `domain` · `out` |
| `domains/*` | 도메인 모듈. 폴더 이름이 도메인 이름이다 |
| `architecture` | 도메인 모듈 안의 계층 규칙(ArchUnit `LayerRules`) |

## 인증

토큰 검증 · 무효화 확인 · 역할 검사 · CORS는 Gateway만 한다. 서비스는 Spring Security 없이 Gateway가 붙인 헤더로 로그인 회원을 안다.

- Gateway는 밖에서 온 `X-Member-*`를 지우고, 검증을 통과한 토큰의 `X-Member-Id` · `X-Member-Roles` · `X-Member-Nickname`(UTF-8 퍼센트 인코딩)을 붙인다. 계약은 `shared`의 `com.bbcc.kidly.shared.auth`(헤더 `MemberHeaders` · 역할 `Role` · 토큰 클레임 이름 `AccessTokenClaims`)다
- 로그인이 필요한 API는 컨트롤러 파라미터로 `@LoginMember AuthMember`(회원 ID · 닉네임)를 받는다. 헤더가 없거나 잘못되면 401이다. 그 밖의 API는 공개다
- `/api/v1/admin/**`는 ADMIN, `/api/v1/seller/**`는 SELLER만 Gateway가 통과시킨다(토큰이 없으면 401, 역할이 없으면 403). 서비스는 역할 경로를 검사하지 않는다
- Gateway는 보안 응답 헤더를 붙이고, 정규화되지 않은 경로 · 위험한 요청은 400으로 막는다

서비스는 Gateway 뒤에서만 열린다는 전제다(dev 8083 · prod 8084 · 로컬 8083). Gateway를 거치지 않고 서비스에 닿을 수 있으면 `X-Member-*` 헤더를 위조할 수 있다. 자세한 Gateway 동작은 [`apps/gateway/CLAUDE.md`](apps/gateway/CLAUDE.md)

요청 로그(`RequestLoggingFilter`)의 요청 헤더에 `x-member-nickname`(퍼센트 인코딩된 닉네임)이 남는다. 로그는 이름 · ID를 남기는 정책이라(`LogMasker`) 가리지 않는다.
