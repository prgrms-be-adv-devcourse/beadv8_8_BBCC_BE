# Kidly BE

자녀 맞춤 키즈 패션 이커머스 백엔드. Spring Boot 4.1 · Java 25 · PostgreSQL 18 · Kafka 모듈러 모놀리스.

개발 규칙은 [`CLAUDE.md`](CLAUDE.md)를 따른다.

## 준비

- JDK 25, Docker Compose를 쓸 수 있는 Docker 환경 (Gradle은 Wrapper를 쓴다)

## 로컬 실행

1. `.env.example`을 루트에 `.env`로 복사한다. 로컬은 기본값이 있어 비워 둬도 된다(앱이 실행될 때 루트의 `.env`를 자동으로 읽는다)
2. `docker compose up -d` — PostgreSQL · Kafka · pgAdmin · Prometheus · Grafana
3. `./gradlew bootRun` — 기본 프로필 local. 루트에서 실행한다(IDE는 공유 실행 구성 `KidlyApplication`)

- pgAdmin: http://localhost:5050 — 로그인 `admin@kidly.dev` / `kidly`, 등록된 PostgreSQL 서버 접속 비밀번호 `kidly` (로컬 개발용)
- Swagger: http://localhost:8080/swagger-ui.html

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
| `common` | 공통 코드(`global`): 이벤트 발행, 메일, 엔티티 기반 클래스, 오류 · 응답 형식 |
| `shared` | 도메인 사이 계약: 이벤트(`DomainEvent`), 도메인별 `event` · `dto` · `domain` · `out` |
| `domains/*` | 도메인 모듈. 폴더 이름이 도메인 이름이다 |
| `architecture` | 도메인 모듈 안의 계층 규칙(ArchUnit `LayerRules`) |
