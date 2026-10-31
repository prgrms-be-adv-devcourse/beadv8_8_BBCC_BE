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
- 프론트와 Swagger(http://localhost:8083/swagger-ui.html)는 Gateway 주소(8083)로 붙는다. 앱(8080)에 직접 붙는 것은 앞으로 로그인이 필요한 API가 안 된다
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
| `apps/gateway` | API Gateway(WebFlux). 토큰 검증 · 무효화 확인 · CORS · 라우팅 |
| `common` | 공통 코드(`global`): 이벤트 발행, 메일, 엔티티 기반 클래스, 오류 · 응답 형식 |
| `shared` | 도메인 사이 계약: 이벤트(`DomainEvent`), 도메인별 `event` · `dto` · `domain` · `out` |
| `domains/*` | 도메인 모듈. 폴더 이름이 도메인 이름이다 |
| `architecture` | 도메인 모듈 안의 계층 규칙(ArchUnit `LayerRules`) |

## 서비스 인증 모드

`kidly.auth.mode`(`common` `SecurityConfig`, 환경 변수 `KIDLY_AUTH_MODE`)로 서비스가 로그인 회원을 어떻게 만들지 정한다.

| 모드 | 동작 | 쓰는 곳 |
|---|---|---|
| `jwt`(기본) | 서비스가 `Authorization: Bearer` Access Token을 직접 검증한다(서명 · 만료 · 무효화). CORS를 켠다 | prod · 로컬 · 테스트 |
| `gateway` | Gateway가 검증하고 붙인 `X-Member-Id` · `X-Member-Roles` · `X-Member-Nickname`(UTF-8 퍼센트 인코딩)으로 로그인 회원을 만든다. JWT는 보지 않고 CORS는 끈다(Gateway가 맡는다) | dev(`deploy/.env.dev`) |

`gateway` 모드는 서비스가 Gateway 뒤에서만 열린다는 전제다. Gateway가 밖에서 온 `X-Member-*` 헤더를 지우므로, Gateway를 거치지 않고 서비스에 닿을 수 있으면 헤더를 위조할 수 있다. 자세한 Gateway 동작은 [`apps/gateway/CLAUDE.md`](apps/gateway/CLAUDE.md)

`gateway` 모드에서는 요청 로그(`RequestLoggingFilter`)의 요청 헤더에 `x-member-nickname`(퍼센트 인코딩된 닉네임)이 남는다. 로그는 이름 · ID를 남기는 정책이라(`LogMasker`) 가리지 않는다.
