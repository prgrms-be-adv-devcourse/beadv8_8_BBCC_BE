# Kidly BE 기본 규율

이 파일은 도메인 사이 외부 계약을 지키기 위한 최소 규율이다. 모든 작업에 적용한다. 도메인 내부는 외부 계약과 공용 실행 · 테스트 환경의 제약 안에서 자율적으로 설계한다.
- 도메인 안의 설계는 그 도메인 폴더에 `CLAUDE.md`가 있으면 그것을, 없으면 그 도메인의 기존 코드 방식을 따른다. 어느 쪽이든 이 파일과 부딪히면 이 파일을 따른다.
- 이 파일에서 "묻는다"는 작업을 멈추고 사람에게 확인받는다는 뜻이다. 확인 전에는 그 부분을 바꾸지 않는다.
- 실행 · 빌드 · 테스트 명령은 README를 따른다: @README.md

## 1. 도메인 경계
- 다른 도메인의 코드를 참조하지 않는다. 도메인 사이는 `shared`의 계약으로만 주고받는다
- 다른 도메인을 동기로 부를 때는 그 도메인의 `shared.{도메인}.out` 인터페이스만 쓴다. 이 인터페이스는 제공하는 도메인의 계약이다: 제공하는 도메인이 구현하고, 호출하는 도메인은 이 인터페이스에만 의존한다
- 자기 DB 스키마만 읽고 쓴다

## 2. `shared` 계약
- `shared`는 JDK만 쓴다. 도메인 · `common`을 참조하지 않는다
- 도메인 간 이벤트는 `shared.{발행도메인}.event`에 record로 만들고 `DomainEvent`(`shared/event/DomainEvent.java`)를 구현한다. 이름은 `{발행도메인}{발생한 사실의 과거형}Event`(예: `OrderCreatedEvent` → `eventType` `order.created`)
- 기존 이벤트의 패키지 · 클래스 이름을 바꾸지 않는다(패키지에서 `producer`, 클래스 이름에서 `eventType` 값이 만들어진다)
- `aggregateId()`는 이벤트가 다루는 대상의 ID, `correlationId()`는 순서를 지켜야 하는 흐름의 ID다(기본값은 `aggregateId()`. 예: 결제 · 구매확정 이벤트는 주문 ID). 보내는 도메인이 정하고, 기존 이벤트의 값을 바꾸기 전에 묻는다(Kafka 키가 된다). 같은 키의 순서는 같은 토픽의 같은 파티션 안에서만 보장된다. 서로 다른 도메인 토픽 사이의 순서는 보장하지 않는다
- 독립 배포: 아래를 지키면 보내는 쪽 · 받는 쪽 중 어느 쪽을 먼저 배포해도 된다
  - 보내는 쪽은 호환되게만 바꾼다: 기존 이벤트 · DTO의 필드는 추가만 한다. 이름 · 타입 · 의미(단위 · 허용값 포함)를 바꾸거나 필드를 지우지 않는다. 이런 변경이 필요하면 묻는다
  - 추가한 필드는 없어도 동작해야 한다: 그 필드가 없는 예전 메시지 · 호출도 역직렬화하고 처리한다(필수값 검사로 실패시키지 않는다)
  - 받는 쪽은 너그럽게 읽는다: 모르는 필드는 무시한다(tolerant reader). 이 설정을 바꾸지 않는다
  - 예외: 새 enum 값은 그 값을 받을 수 있는 모든 도메인이 처리하도록 배포된 뒤에 보낸다
- 이벤트에 개인정보 필드를 추가하지 않는다
- ID는 `Long`, 시각은 `LocalDateTime`(한국 시간, 시간대 표시 없음)

## 3. `common` 모듈(`global` 패키지)
- `common` 모듈의 코드는 `com.bbcc.kidly.global` 패키지에 있다. 도메인은 `common`에 의존할 수 있고, `common`은 도메인을 참조하지 않는다
- 이벤트는 `EventPublisher`로, `@Transactional` 메서드 안에서 발행한다(트랜잭션 밖에서 발행하면 전달되지 않는다)

## 4. API
- API의 자원 · 용어 · 처리 책임은 자기 도메인 기준으로 정한다. 다른 화면이나 도메인의 요청으로 API를 추가할 수는 있지만, 그쪽 업무 흐름에 맞춘 API는 만들지 않는다
- REST 관례(자원 중심 URL, HTTP 메서드 · 상태 코드)를 따른다
- 실패 응답은 `GlobalExceptionHandler`의 ProblemDetail로 낸다. 도메인 오류는 `ErrorCode`를 구현한 enum과 `BusinessException`을 쓴다

## 5. 함께 쓰는 것
- 다음은 바꾸기 전에 묻는다: `application*.yml`, `common`, `shared`(단, 자기 도메인 패키지 `shared.{자기도메인}`에 2절 조건을 지키는 필드를 추가하는 것은 묻지 않는다), 빌드 설정(`build.gradle.kts` · `build-logic` · `gradle`), 공통 테스트 기반(`support`, `TestcontainersConfiguration`), 새 의존성
- 이미 적용된 Flyway 파일은 고치지 않는다. 새 파일을 추가한다
- 도메인 yml(`{도메인}.yml`)의 키와 빈 이름은 다른 도메인과 겹치지 않게 한다. `spring.*` · `management.*`에는 그 도메인만 쓰는 항목만 둔다
- 로그 · 예외 메시지에 개인정보를 넣지 않는다
- `@IntegrationTest` · `@FunctionalTest` · `@KafkaIntegrationTest`를 쓰는 테스트 클래스에 개별 설정(`@MockitoBean`, `@TestPropertySource` 등)을 붙이지 않는다(붙이면 스프링 컨텍스트가 새로 뜬다). 테스트에 특별한 설정이 필요하면 먼저 묻는다. 공통 테스트 기반에 반영할지는 다른 도메인에 미치는 영향을 보고 정한다
- Kafka 컨테이너는 `@KafkaIntegrationTest`에서만 뜬다. 커밋 뒤 Kafka로 보내는 빈에는 `@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)`를 붙인다

## 6. 완료 조건
- `./gradlew build`가 통과한다(ArchUnit 포함). ArchUnit에 예외를 추가하지 않는다
- 브랜치 `feature|fix|hotfix/{도메인}/#{이슈}-{기능}`. main · develop에 직접 push하지 않는다. 커밋 제목 `{타입}: {요약}`

## 7. Kafka

### 보내는 쪽
- 토픽은 자기 도메인 것 하나: `kidly.{보내는도메인}.events`. 자기가 `NewTopic`으로 선언한다(브로커 자동 생성은 꺼져 있다)
- 파티션 3 · 복제 1. 만든 뒤 바꾸지 않는다(늘리면 같은 키의 순서가 깨진다)
- 키는 `correlationId()`. 헤더는 `eventType` · `eventId` 두 개. `__TypeId__` 헤더는 넣지 않는다
- 본문은 JSON 문자열: 공통 칸 7개(`eventId` · `eventType` · `schemaVersion` · `occurredAt` · `producer` · `aggregateId` · `correlationId`) + `data`. record를 그대로 직렬화하지 않는다(5칸이 빠진다). `data`가 `null`이면 보내지 않는다
- DB 커밋이 끝난 뒤에만 보낸다
- 다음은 바꾸기 전에 묻는다: 이벤트 추가 · 중단 · 삭제, 토픽, 키, 메시지 형식

### 받는 쪽
- 구독 그룹은 `kidly.{받는도메인}`만 쓴다

### 침범 금지
- 다른 도메인의 토픽을 만들거나 그 토픽에 보내지 않는다. 받은 메시지를 원래 토픽에 다시 넣지 않는다
- 다른 도메인의 그룹 · DLT · 오프셋을 쓰거나 바꾸지 않는다
- 특정 받는 도메인에 맞춰 이벤트를 바꾸지 않는다
- 앱 전체 Kafka 설정(`spring.kafka.*`)은 `application.yml`에만 둔다. 도메인만 쓰는 Kafka 값은 도메인 yml의 `kidly.{도메인}.kafka.*`에 두고, 자기 리스너 팩토리 · 템플릿 코드에서 적용한다(한 앱인 동안 `spring.kafka.*`는 도메인끼리 서로 덮어쓴다)
- 도메인 전용 리스너 팩토리는 빈으로 등록할 수 있다(`{도메인}KafkaListenerContainerFactory`). 도메인 전용 `KafkaTemplate` · `ProducerFactory` · `ConsumerFactory` · 에러 처리기가 필요하면 그 도메인의 구성 코드 안에서 만들어 쓰고, 각각을 빈으로 등록하지 않는다
- 로그 · 예외에 메시지 본문을 넣지 않는다. 남기는 값은 `eventType` · `eventId` · 키뿐이다
