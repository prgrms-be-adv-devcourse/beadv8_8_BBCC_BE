# Kidly BE

자녀 맞춤 키즈 패션 이커머스. Spring Boot 4.1 / Java 25 / PostgreSQL 18 모듈러 모놀리스.

## 명령어
- 로컬 DB: `docker compose up -d` (pgAdmin: http://localhost:5050, 서버 비밀번호 `kidly`)
- 실행: `./gradlew bootRun` (기본 프로필 local)
- 빌드(Checkstyle, ArchUnit, 테스트 포함): `./gradlew build`
- 경계 규칙만 검사: `./gradlew archTest` (`test`에서는 제외된다)
- 단위 테스트: `./gradlew test` (Docker 불필요) / 통합: `./gradlew integrationTest` / 기능: `./gradlew functionalTest` (둘 다 Docker 필요)
- Swagger: http://localhost:8080/swagger-ui.html

## 구조
- `com.bbcc.kidly.global`: 공통 코드. 도메인 패키지를 참조하지 않는다
  - `eventpublisher`: `EventPublisher`. 이벤트는 `ApplicationEventPublisher` 대신 이것으로 발행한다
    - 발행할 수 있는 건 `shared.event.DomainEvent`를 구현한 record뿐이다
    - 발행은 `@Transactional` 서비스 안에서 `eventPublisher.publish(new PaymentCompletedEvent(...))`. 트랜잭션 밖에서 발행하면 전달되지 않는다
    - 수신은 받는 도메인 `app`의 `@Component`에 `@DomainEventListener public void on(PaymentCompletedEvent event)`. 발행한 쪽이 커밋된 뒤 새 트랜잭션에서 실행된다
    - 리스너가 실패하면 리스너 트랜잭션만 롤백되고 예외는 ERROR 로그로만 남는다 (발행한 쪽은 이미 커밋됨)
- `com.bbcc.kidly.shared`: 도메인 사이 계약. 도메인 패키지와 `global`을 참조하지 않는다
  - `event.DomainEvent`: 모든 도메인 간 이벤트가 구현하는 인터페이스. 기획서 6.2 공통 형식이고, 이벤트마다 다른 내용은 `data`에 담는다
    - record가 필드로 갖는 것: `UUID eventId`(발행할 때 `UUID.randomUUID()`), `LocalDateTime occurredAt`, `Data data`(이벤트 record 안의 `Data` record)
    - 예: `record PaymentCompletedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent`, 꺼낼 때 `event.data().orderId()`
    - record가 구현하는 것: `String aggregateId()`(이벤트가 다루는 대상의 ID)
    - 기본값: `eventType`(클래스 이름에서, `OrderCreatedEvent` → `order.created`. 대상이 두 단어 이상이면 재정의), `producer`(패키지 `shared.{도메인}.event`에서), `schemaVersion` 1, `correlationId` = `aggregateId`
    - 복사본은 저장된 값보다 `occurredAt`이 늦은 이벤트만 반영한다
  - 도메인별 패키지 `member`, `product`, `recommend`, `order`, `payment`, `settlement`
    - `dto`: 도메인 사이에 주고받는 데이터
    - `domain`: 다른 도메인이 복사해 두는 그 도메인 데이터의 복사본
    - `event`: 그 도메인이 발행하는 도메인 간 이벤트. record로 만들어 `DomainEvent`를 구현하고, 이름은 `발행도메인+과거형+Event`(예: `shared.payment.event.PaymentCompletedEvent`). 엔티티 대신 ID와 그 시점의 값만 `data`에 담는다
    - `out`: 다른 도메인이 그 도메인과 연결할 때 쓰는 계약
- `com.bbcc.kidly.boundedcontext`: 도메인 `member`, `product`, `recommend`, `order`, `payment`, `settlement`
  - `app`: 유스케이스 서비스, 이벤트 리스너
  - `in`: 들어오는 요청. 컨트롤러, `dto`의 XxxRequest/XxxResponse
  - `domain`: 엔티티, 상태 enum, 리포지토리 인터페이스, `event`(도메인 내부 이벤트)
  - `out`: 나가는 연결. QueryDSL 구현, 외부 API 클라이언트
  - 도메인끼리는 서로의 코드를 참조하지 않는다. `shared`의 이벤트로만 주고받는다
- 각 도메인은 자기 DB 스키마만 쓴다: `member`, `product`, `recommend`, `orders`, `payment`, `settlement`
- 경계 규칙은 `src/test/java/com/bbcc/kidly/architecture/ArchitectureTest.java`에서 검사한다

## 코드 컨벤션
- TDD (RED → GREEN → REFACTOR)
- camelCase: 변수·메서드 / PascalCase: 클래스 / UPPER_SNAKE_CASE: 상수 / snake_case: DB 테이블·컬럼
- 엔티티에 `@Data`, public setter 금지. 상태는 업무 메서드로만 바꾼다
- 엔티티는 `global.entity.BaseTimeEntity`를 상속한다 (`created_at`, `updated_at` 자동 기록, `LocalDateTime`)
- 소프트 삭제하는 엔티티는 `global.entity.BaseSoftDeleteEntity`를 상속한다 (`deleted_at`). 서비스에서 `entity.delete(LocalDateTime.now())`로 삭제하고, 삭제된 행을 조회에서 빼려면 엔티티에 `@SQLRestriction("deleted_at IS NULL")`을 붙인다
- 마이그레이션 도구(Flyway)는 쓰지 않는다. `ddl-auto`는 로컬 update, 그 외 validate

## 테스트
- 도메인 테스트는 `src/test/java/com/bbcc/kidly/boundedcontext/{도메인}`에 둔다. `app`·`domain`·`in`·`out` 하위 폴더는 만들지 않는다
- `global`·`shared` 테스트는 대상과 같은 패키지에 둔다
- 종류별 규칙

| 종류 | 대상 | 이름 | 붙일 것 |
|---|---|---|---|
| 단위 | 엔티티 업무 메서드, 값 객체, 서비스 로직 | `XxxTest` | 없음. 서비스는 `@ExtendWith(MockitoExtension.class)` + `@Mock` |
| 통합 | 리포지토리 쿼리, 서비스 + DB, 이벤트 발행 → 수신 | `XxxIntegrationTest` | `support.IntegrationTest` |
| 기능 | API 하나를 HTTP 요청부터 응답까지 | `XxxApiTest` | `support.FunctionalTest` + `RestTestClient` 주입 |

- `@IntegrationTest`·`@FunctionalTest`에 설정(`@MockitoBean`, `@TestPropertySource` 등)을 더하지 않는다. 설정이 다르면 스프링 컨텍스트를 새로 띄워 느려진다
- 통합·기능 테스트는 끝날 때마다 모든 테이블을 비운다(`DatabaseCleanerExtension`). 테스트에 `@Transactional`을 붙여 롤백하지 않는다 (커밋되지 않으면 `@DomainEventListener`가 실행되지 않는다)
- 테스트용 엔티티는 도메인별 `XxxFixture`의 정적 메서드로 만든다

## DB 명명
- PK `id`, FK `참조테이블단수_id`, 불리언 `is_/has_/can_`, 일시 `_at`, 날짜 `_on/_date`
- 수량 `_count/_qty`, 금액 `_amount/_price`, 감사 컬럼 `created_at`, `updated_at`, `deleted_at`
- 예약어(order, user, group, key, value 등) 금지, 부정형 불리언 금지

## API 응답
- 성공은 감싸지 않고 그대로. 조회 200, 생성 201(+Location), 삭제 204
- 실패는 ProblemDetail 5필드(`type, title, status, detail, instance`), `application/problem+json`
  - 도메인 오류는 `global.error.ErrorCode`를 구현한 enum(예: `OrderErrorCode`)을 만들고 `BusinessException`을 던진다
  - `GlobalExceptionHandler`가 업무 예외·검증 실패·스프링 기본 예외·예상 못 한 예외(500)를 변환한다. 401·403은 `SecurityProblemHandler`
- 리스트는 null 대신 `[]`, 없는 숫자·선택 문자열·중첩 객체는 `null`, Boolean은 non-null
- 단건 조회 결과가 없으면 404
- 목록은 `global.response.PageResponse.from(page.map(XxxResponse::from))`: `content, page, size, totalElements, totalPages, hasNext`

## Git
- 브랜치 `feature/도메인/#이슈-기능`, `fix/...`, `hotfix/...`. main·develop 직접 push 금지
- 커밋 `타입: 요약` (feat, fix, docs, style, refactor, test, chore), 제목 50자, 마침표 없음
