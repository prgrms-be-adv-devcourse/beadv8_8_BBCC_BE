# Kidly Kafka 이벤트 규칙

## 0. 원칙

**바꾸면 다른 도메인이 깨지는 것은 팀 규칙으로 지키고, 다른 도메인에 영향이 없는 구현은 각 도메인이 정합니다.**

- **팀 규칙**: 토픽, 키, 메시지 모양, 이름 규칙, 한 앱 안의 공용 설정입니다.
- **도메인 자율**: 리스너 모양, 재시도 정책, 트랜잭션 경계, outbox, 중복 처리 방법입니다. 이 문서는 권장값만 적습니다.
- Kafka 코드는 각 도메인이 자기 모듈 안에 둡니다. 팀 규칙을 지키는지는 각 도메인이 자기 테스트로 확인합니다.
- 판단 기준은 하나입니다. **"이것을 바꾸면 다른 도메인이 깨지는가?"** 깨지면 팀 규칙이고, 깨지지 않으면 도메인 자율입니다.

---

## 1. 팀 규칙 (필수)

| 항목 | 규칙 |
|---|---|
| 토픽 | `kidly.{보내는도메인}.events`. 보내는 도메인마다 하나이며, 그 도메인이 보내는 이벤트는 종류와 관계없이 모두 이 토픽에 넣습니다 |
| 표기 | 소문자만 씁니다. 단계는 `.`, 단어는 `-`로 구분합니다. `_`, 대문자, 환경 이름, 버전은 넣지 않습니다 |
| 파티션 · 복제 | 3 · 1. 만든 뒤에는 파티션 수를 바꾸지 않습니다. 늘리면 키가 들어가는 파티션이 바뀌어 순서가 깨집니다 |
| 토픽 생성 | 보내는 도메인이 `NewTopic` 빈으로 만듭니다. 받는 도메인은 자기 DLT만 만듭니다. 브로커의 토픽 자동 생성은 꺼져 있습니다 |
| 키 | `event.correlationId()` |
| 본문 | JSON 문자열. 공통 칸 7개(`eventId`, `eventType`, `schemaVersion`, `occurredAt`, `producer`, `aggregateId`, `correlationId`) + `data`. 이벤트 record를 그대로 JSON으로 바꾸면 5칸이 빠지므로(5칸은 필드가 아니라 `DomainEvent`의 기본 메서드) 보내는 쪽이 직접 채웁니다 |
| 헤더 | `eventType`, `eventId`(본문과 같은 값). Java 타입 헤더 `__TypeId__`는 쓰지 않습니다 |
| 값 | ID는 `Long`(JSON 숫자). 시각은 `LocalDateTime` ISO 문자열이며 한국 시간이고 시간대 표시를 붙이지 않습니다. `data`가 없는 메시지는 보내지 않습니다 |
| 이벤트 변경 | 필드는 **추가만** 합니다(삭제, 이름 변경, 타입 변경 금지). 개인정보 필드를 더 넣지 않습니다 |
| 모르는 필드 | 받는 쪽은 **모르는 필드를 무시합니다**(Jackson 3, spring-kafka 기본값이며 이 설정을 바꾸지 않습니다) |
| 새 enum 값 | **받는 쪽을 먼저 배포**한 뒤 보냅니다 |
| 읽기 실패 | 모르는 enum 값처럼 메시지를 읽지 못하면 null로 읽거나 건너뛰지 않습니다. **재시도 없이 DLT로 보내고 Slack으로 알립니다**(`READ_UNKNOWN_ENUM_VALUES_AS_NULL`을 켜지 않습니다) |
| 보내는 시점 | **DB 커밋이 확정된 뒤에만** 보냅니다. 방법은 도메인 자율입니다(4절) |
| 구독 그룹 | `kidly.{받는도메인}` |
| DLT | `{토픽}.{받는도메인}.dlt`, 파티션 1. 받는 도메인이 만듭니다. 처리는 사람이 확인한 뒤 그 받는 도메인이 DLT를 읽는 별도 consumer로 다시 합니다. **원래 토픽에 다시 넣지 않습니다.** "나중에 다시 하기"용 retry 토픽은 쓰지 않습니다. DLT 전송기(`DeadLetterPublishingRecoverer`)의 기본 목적지(`{토픽}-dlt`, 원래 파티션 번호)는 쓰지 않고, 목적지를 이 이름과 파티션 `-1`(Kafka가 고름)로 지정합니다(3절 리스너 설정 예시) |
| 자기 이벤트 | 자기 도메인 이벤트는 Kafka로 받지 않고 Spring 이벤트로 받습니다(member의 `member.withdrawn`, product의 `product.changed`) |
| 로그 | 메시지 본문을 로그와 예외 메시지에 넣지 않습니다. 남기는 것은 `eventType`, `eventId`, 키뿐입니다(본문의 개인정보가 Kafka와 DLT에 7일 남습니다) |

### 한 앱(`kidly-app`)인 동안의 공용 규칙

여러 도메인이 한 앱에 있어서, 아래를 어기면 다른 도메인이 깨집니다.

| 항목 | 규칙 |
|---|---|
| 앱 설정 | Kafka 설정은 앱 `application.yml` 한 곳에만 있습니다. String 직렬화, `acks=all`, `enable.idempotence=true`, `auto-offset-reset: latest`가 들어 있고 `max.block.ms`와 브로커 `offsets.retention.minutes`는 기본값입니다 |
| 도메인 yml | `spring.kafka.*`를 넣지 않습니다. 나중에 불러온 도메인 yml이 앞의 값을 조용히 덮어씁니다. 도메인 값은 자기 리스너 설정 코드에 둡니다 |
| 빈 | `KafkaTemplate`, `ProducerFactory`, `ConsumerFactory`를 도메인에서 빈으로 새로 만들지 않습니다(Boot 기본 빈이 사라집니다) |
| 빈 이름 | 도메인이 만드는 컨테이너 팩토리와 에러 처리기 빈은 도메인 이름을 앞에 붙입니다(`{도메인}KafkaListenerContainerFactory`). 이름이 겹치면 앱이 뜨지 않습니다 |
| 에러 처리기 | 자기 컨테이너 팩토리에 직접 붙입니다. 에러 처리기 빈이 앱에 둘 이상이면 Boot가 기본 팩토리에 아무것도 붙이지 않습니다 |
| 의존성 | `spring-boot-starter-kafka`를 `kidly.domain` 빌드 플러그인으로 모든 도메인이 받습니다. 도메인 빌드 파일에 따로 넣지 않습니다 |
| 보내기 스위치 | 커밋 뒤 Kafka로 보내는 빈(아웃박스를 쓰면 릴레이 스케줄러)에는 `@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)`를 붙입니다. Kafka 컨테이너가 없는 테스트는 이 값을 꺼서 보내지 않습니다 |
| 전송 실패 로그 | 앱은 Boot 기본 `KafkaTemplate`의 전송 실패를 토픽 · 키 · `eventType` · `eventId`와 예외로 남기고 본문은 남기지 않습니다(앱 설정). 받는 쪽 DLT 전송기처럼 Boot 기본 템플릿을 쓰는 곳은 따로 할 일이 없습니다 |
| 직접 만든 템플릿 | `new KafkaTemplate<>(...)`으로 만든 템플릿에는 앱 설정이 붙지 않고, 기본 실패 로그가 키와 본문을 그대로 남깁니다. 만들 때 `setProducerListener(new ProducerListener<>() {})`로 기본 실패 로그를 끄고, 실패는 자기 코드에서 `eventType` · `eventId` · 키로 남깁니다 |

---

## 2. 토픽 목록

| 토픽 | `eventType` | 키 | 보내는 도메인 | 받는 도메인 |
|---|---|---|---|---|
| `kidly.member.events` | `member.registered` | 회원 ID | member | notification, payment |
| | `member.suspended` | 회원 ID | | notification |
| | `member.changed` | 회원 ID | | seller |
| | `member.withdrawn` | 회원 ID | | auth, childprofile, member(배송지), payment |
| `kidly.seller.events` | `seller.changed` | 판매자 ID | seller | member, order, product, recommend, search |
| | `seller.application-rejected` | 신청 ID | | notification |
| `kidly.childprofile.events` | `child.changed` | 자녀 ID | childprofile | auth, order, recommend |
| `kidly.auth.events` | `auth.tokens-revoked` | 토큰 주체 ID | auth | gateway |
| `kidly.product.events` | `product.changed` | 상품 ID | product | community, order, recommend, search |
| | `stock.deducted` · `stock.deduction-failed` | 주문 ID | | order |
| `kidly.order.events` | `order.created` | 주문 ID | order | product |
| | `order.payment-requested` | 주문 ID | | payment |
| | `order.cancelled` | 주문 ID | | payment, product |
| | `order-item.cancelled` | 주문 ID | | payment, product |
| | `order-item.purchase-confirmed` | 주문 ID | | community, recommend, settlement |
| | `cart.item-added` | 회원 ID | | recommend |
| `kidly.payment.events` | `payment.completed` · `payment.failed` | 주문 ID | payment | order |
| | `payment.refunded` | 주문 ID | | (없음) |
| | `payment.payout-completed` · `payment.payout-failed` | 정산서 ID | | settlement |
| `kidly.settlement.events` | `settlement.payout-requested` | 정산서 ID | settlement | payment |
| `kidly.search.events` | `search.performed` | 회원 ID | search | recommend |

- 순서는 **같은 토픽 + 같은 키** 안에서만 보장됩니다. 한 주문의 이벤트(생성 → 결제 요청 → 취소 → 구매확정)는 모두 `kidly.order.events`에 주문 ID 키로 들어가므로 순서가 지켜집니다.
- member는 `seller.changed`를 회원 서비스에서 Kafka로 받습니다. 토큰 무효화는 auth가 `auth.tokens-revoked`를 보내고 Gateway가 받아 처리합니다.
- 받는 도메인은 11-04 코드의 `@KafkaListener` 기준입니다. 그 밖의 주문 이벤트(장바구니 빼기, 위시 관련)는 Kafka로 받는 도메인이 없어 표에 넣지 않았습니다.
- 새 이벤트를 만들거나 토픽, 키, 메시지 형식을 바꾸려면 팀에 먼저 묻습니다. 결정되면 이 표에 추가합니다.

---

### 알림용 이벤트 데이터 계약

- `member.suspended` (`MemberSuspendedEvent`): `memberId`, `reason`, `suspendedAt`. 키는 회원 ID. 관리자 정지 시 저장한 정지 시각과 `occurredAt`, `suspendedAt`이 같다. 기존 `auth.tokens-revoked`도 유지한다.
- `seller.application-rejected` (`SellerApplicationRejectedEvent`): `applicationId`, `memberId`(신청자), `reason`, `rejectedAt`. 키는 신청 ID. 저장한 결정 시각과 `occurredAt`, `rejectedAt`이 같다.
- 두 이벤트는 상태 변경 트랜잭션에서 발행하고 기존 릴레이로 DB 커밋 후 Kafka에 전송한다. 롤백 시 전송하지 않는다. 기존 비동기 직접 전송 방식이므로 장애 시 유실될 수 있다.
- `reason`은 대상 회원에게 안내할 사유다. 이벤트 본문과 사유를 로그에 남기지 않는다.
- 수신 도메인 `notification`은 위 이벤트의 예정 수신자다. 메일·알림함 처리는 알림 도메인에서 구현한다.
- 기존 회원 이메일은 Kafka에 보관된 `member.registered`를 알림 도메인이 재소비해 채운다. `member.registered` 데이터(`memberId`, `email`)는 유지하며 `resent` 추가나 회원 도메인의 재발행은 하지 않는다.

---

## 3. 코드 배치

| 위치 | 담는 것 | 담지 않는 것 |
|---|---|---|
| `shared` | 이벤트 record(바뀌지 않음, 필드는 추가만) | Kafka 코드 |
| `common` | `EventPublisher`(Kafka를 모른 채 Spring 이벤트만 발행) | Kafka 코드와 설정 |
| 보내는 `domains/*` | `out`에 토픽 선언(`NewTopic`)과 커밋 뒤 발행 빈 | 다른 도메인 토픽 |
| 받는 `domains/*` | 자기 리스너 설정(컨테이너 팩토리, 에러 처리기, DLT `NewTopic`)과 `@KafkaListener` 리스너. 리스너 패키지는 지금 위치 그대로(대부분 `in`, payment는 `app`) | `spring.kafka.*` yml |
| `apps/kidly-app` | 앱 공용 Kafka 설정, Kafka 테스트 지원(`@KafkaIntegrationTest`, `support.AsyncWait`) | 도메인별 Kafka 설정 |

### 예시 코드와 맞춰야 하는 것

아래 코드는 모양을 보여 주는 예시입니다. 클래스 이름, 중계 빈과 전송 클래스를 나눌지, 전송 스레드, 리스너 안에서 종류를 나누는 방법은 각 도메인이 정합니다. 예를 들어 회원 · 판매자 · 자녀는 중계 빈과 전송 클래스를 나누고 전용 스레드에서 보내고, 결제 · 상품은 아웃박스에 저장한 뒤 릴레이가 보냅니다.

다만 다른 도메인의 코드도 쉽게 알아볼 수 있도록, 예시의 구조와 이름 짓는 방식을 비슷하게 유지하기를 권장합니다. 토픽과 보내는 도메인 이름을 상수로 두기, 커밋 뒤에 보내는 중계 빈, 토픽마다 리스너 클래스 하나와 그 안의 `switch` 분기가 여기에 해당합니다. 권장이며 강제하지는 않습니다.

모든 도메인이 똑같이 맞춰야 하는 것은 1절의 팀 규칙입니다. 다른 도메인이 받는 메시지와 이름에 관한 규칙이기 때문입니다. 토픽 이름, 키, 본문(공통 칸 7개 + `data`), 헤더 두 개, 커밋 뒤에 보내기, 보내기 스위치, 구독 그룹과 DLT 이름이 여기에 해당합니다.

### 보내는 쪽

커밋 뒤 바로 보내는 실제 코드는 회원의 `MemberEventRelay`(커밋 뒤 중계)와 `out.kafka`(토픽 선언, 전송)를, 아웃박스로 보내는 코드는 결제와 상품을 참고합니다. 요지는 아래와 같습니다.

```java
// domains/order/.../out/OrderEventSender.java (요지)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
class OrderEventSender {

    private static final String TOPIC = "kidly.order.events";
    private static final String PRODUCER = "order";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;   // Spring이 만든 Jackson 3 빈. new로 만들지 않는다

    // 발행한 트랜잭션이 커밋된 뒤에만 실행된다. 롤백되면 나가지 않는다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void send(DomainEvent event) {
        // 한 앱에 도메인이 여럿이라 자기 이벤트만 보낸다
        if (!event.producer().equals(PRODUCER)) {
            return;
        }
        // 본문 = 공통 칸 7개 + data, 키 = correlationId, 헤더 = eventType · eventId
        var record = new ProducerRecord<>(TOPIC, event.correlationId(), toBody(event));
        record.headers()
            .add("eventType", event.eventType().getBytes(UTF_8))
            .add("eventId", event.eventId().toString().getBytes(UTF_8));
        kafkaTemplate.send(record);
    }
}
```

- `producer()` 검사는 문자열 비교라서 오타가 나면 아무것도 보내지 않는데도 에러가 나지 않습니다. "우리 도메인 이벤트가 우리 토픽에 키, 헤더, 본문 7칸으로 나간다"는 테스트를 `@KafkaIntegrationTest`로 둡니다(결제 `PaymentKafkaPublishIntegrationTest` 참고).
- 위 예시처럼 Boot 기본 `KafkaTemplate`을 쓰면 전송 실패는 앱 설정이 토픽 · 키 · `eventType` · `eventId`로 남깁니다. 전용 템플릿을 직접 만들면 1절 "직접 만든 템플릿"대로 기본 실패 로그를 끄고 실패를 직접 남깁니다(결제 · 회원 · 판매자 · 자녀 · 상품이 이렇게 합니다).

### 받는 쪽

```java
// domains/payment/.../app/OrderEventKafkaListener.java (요지)
@KafkaListener(topics = "kidly.order.events", groupId = "kidly.payment",
               containerFactory = "paymentKafkaListenerContainerFactory")
@Transactional
public void on(ConsumerRecord<String, String> record) {
    switch (header(record, "eventType")) {
        case "order.payment-requested" -> handle(read(record, OrderPaymentRequestedEvent.class));
        case "order.cancelled" -> handle(read(record, OrderCancelledEvent.class));
        default -> { }   // 이 도메인이 받지 않는 이벤트 종류
    }
}
```

- `default`에서 건너뛰는 것은 "이 도메인이 받지 않는 이벤트 종류"뿐입니다. 받는 종류인데 읽지 못하면 1절의 "읽기 실패" 규칙을 따릅니다.

### 받는 쪽 리스너 설정

```java
// domains/payment/.../app/PaymentKafkaListenerConfig.java (요지)
@Bean
ConcurrentKafkaListenerContainerFactory<String, String> paymentKafkaListenerContainerFactory(
        ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate) {
    // DLT 목적지: {원래토픽}.{받는도메인}.dlt, 파티션 -1(Kafka가 고름). 기본 목적지({토픽}-dlt)는 쓰지 않는다
    var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
        (record, ex) -> new TopicPartition(record.topic() + ".payment.dlt", -1));
    // 재시도 값은 도메인 자율. 메시지를 읽지 못한 예외는 재시도하지 않고 바로 DLT로 보낸다
    var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    errorHandler.addNotRetryableExceptions(JacksonException.class);

    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler);   // 에러 처리기는 빈으로 두지 않고 여기에 직접 붙인다
    return factory;
}
```

- 모든 도메인이 똑같이 맞춰야 하는 것은 DLT 목적지 함수(이름 규칙과 파티션 `-1`)입니다. 목적지를 넘기지 않으면 기본 목적지 `{토픽}-dlt`로 보내는데, 이 토픽은 없고 자동 생성도 꺼져 있습니다. 그러면 DLT 전송이 `max.block.ms`(60초) 동안 기다리다 실패하고, 같은 메시지를 다시 처리하고 다시 DLT로 보내려는 일이 반복되며 그 파티션의 뒤 메시지가 모두 멈춥니다.
- 재시도 횟수와 간격은 도메인이 정합니다(4절).

---

## 4. 도메인 자율 (권장 · 주의)

### 반드시 알아야 할 함정 (어기면 조용히 틀어짐)

- **같은 토픽 · 같은 그룹에 리스너 클래스를 둘 이상 두지 않습니다.** 둘이면 파티션을 나눠 가져서 각 클래스가 메시지 일부만 받습니다. 에러는 나지 않습니다.
- **리스너에서 예외를 잡아서 숨기지 않습니다.** 숨기면 재시도도 DLT 이동도 일어나지 않습니다.
  - 예외: 자기 실패 보관이 있는 도메인(order의 `order_event_failure`, `payment_recovery`)은 보관에 성공하면 정상 반환하고, 보관에 실패하면 예외를 던집니다. 이 사실을 리스너 주석에 적습니다.
- **실패 보관이 리스너 트랜잭션에 합류하지 않게 합니다.** 합류한 상태에서 예외를 삼키고 정상 반환하면 커밋할 때 `UnexpectedRollbackException`이 납니다(트랜잭션이 이미 rollback-only 상태입니다).
- **같은 메시지가 두 번 올 수 있습니다.** Kafka는 "적어도 한 번" 전달합니다. 같은 메시지를 두 번 받아도 결과가 1회분인지 테스트로 확인합니다.
- **DLT로 빠진 메시지가 있으면 같은 키의 다음 메시지가 먼저 처리됩니다.** 처리 전에 "지금 상태에서 적용할 수 있는가"를 확인합니다.
- **같은 이벤트를 `@DomainEventListener`와 Kafka 양쪽에서 받지 않습니다.**

### 각 도메인이 정하는 것 (권장 예시)

| 항목 | 권장 예시 | 비고 |
|---|---|---|
| 보내는 방법 | 커밋 뒤 중계 빈(`@TransactionalEventListener(AFTER_COMMIT)` + `producer()` 확인). 실패하면 본문 없이 ERROR 로그 | 돈 · 재고처럼 이벤트를 잃으면 안 되는 흐름은 outbox를 발행 코드를 만들기 전에 검토합니다. 전송 작업은 한 번에 하나만 돌게 합니다(여러 개면 순서가 뒤집힙니다). 결제 · 상품 · 주문은 아웃박스, 회원 · 판매자 · 자녀는 전용 스레드 하나에서 커밋 뒤 바로 보냅니다 |
| 전송 대기 시간 | 앱 기본값(`max.block.ms` 60초) | 브로커가 응답하지 않으면서 그 토픽 정보가 없을 때(앱 시작 직후 첫 전송, 5분 넘게 보내지 않은 토픽) `send()`가 이만큼 기다립니다. 커밋 뒤 요청 스레드에서 보내면 그 응답뿐 아니라, 같은 요청에서 커밋 뒤에 도는 다른 도메인의 `@DomainEventListener`도 함께 늦어집니다. 줄이고 싶으면 자기 템플릿에 짧은 값을 주는 정도면 충분합니다(지금 보내는 도메인은 전용 템플릿에서 3 ~ 5초로 둡니다). 아웃박스를 쓰면 릴레이가 따로 보내므로 요청 스레드가 기다리지 않습니다. 전용 스레드나 아웃박스는 필수가 아닙니다 |
| 리스너 모양 | 토픽마다 클래스 하나 + 안에서 `switch`로 종류를 나눔 | |
| 트랜잭션 경계 | 처리 메서드에 `@Transactional` | `@KafkaListener`는 `@DomainEventListener`와 달리 새 트랜잭션을 열지 않습니다. 실패 보관이 있으면 처리를 자기 트랜잭션으로 분리합니다(주문이 이렇게 합니다) |
| 재시도 | 처음 1번 + `FixedBackOff(1000L, 3)`. 다시 해도 같은 예외(`BusinessException`, `IllegalArgumentException`, `IllegalStateException`, NPE)는 바로 DLT | 읽기 실패는 1절대로 재시도하지 않습니다 |
| ack mode | `RECORD` | |
| 중복 처리 | 업무 상태 검사, UNIQUE 제약, `occurredAt` 비교, 처리 기록 표 중 선택 | |
| 빈 List | 옛 메시지에 없는 List는 빈 목록으로 다룹니다 | |

---

## 5. 옮기는 순서

### 받는 쪽을 옮기기 전에 끝나 있어야 하는 것

1. 결제 마감 만료 · 정산 스케줄러의 실행 조건(6절 미정): order, settlement가 받는 쪽을 옮기기 전
2. 상품 샘플 데이터 러너 수정: `ProductSampleDataService`는 판매자 복사본이 없으면 샘플을 조용히 건너뜁니다. 복사본이 Kafka로 늦게 채워지면 샘플이 생기지 않습니다

### 순서

1. **보내기 켜기**: 아직 보내지 않는 도메인(order, settlement)이 토픽 선언과 보내기를 넣습니다. Spring 이벤트는 그대로 두므로 동작은 바뀌지 않습니다. dev에서 그 도메인의 흐름을 한 번 실행해 토픽, 키, 본문, 헤더를 확인합니다.
2. **받는 쪽을 하나씩 옮긴다**: recommend → settlement → product → payment → order → notification · childprofile · seller. 그 이벤트를 보내는 도메인이 1을 마친 뒤에 시작합니다.

### 받는 쪽을 옮기는 PR마다 확인할 것

- [ ] 보내는 도메인이 그 토픽으로 이미 보내고 있다
- [ ] 토픽마다 리스너 클래스가 하나다
- [ ] 같은 이벤트의 `@DomainEventListener`를 같은 PR에서 지웠다
- [ ] 같은 메시지 두 번 → 결과 1회분 테스트가 있다
- [ ] 발행 직후 결과를 단언하던 통합 테스트를 `@KafkaIntegrationTest`로 옮기고 `support.AsyncWait.until(...)`로 기다리게 바꿨다
- [ ] 수신 테스트는 그 테스트 안에서만 리스너를 켜고 끄며(`KafkaListenerEndpointRegistry`), 테스트마다 고유한 키를 쓰고 그 키의 결과만 확인한다
- [ ] 테이블을 비우기 전에 처리 중인 메시지가 끝나기를 기다린다(첫 수신 PR에서 넣는다)
- [ ] 받는 쪽 DLT `NewTopic`이 있고, DLT 전송기 목적지가 `{토픽}.{받는도메인}.dlt`, 파티션 `-1`이다
- [ ] 로그 · 예외 메시지에 본문 값이 없다
- [ ] 배포 뒤 그 그룹의 lag 0, 빈 DLT, 옮긴 흐름이 dev에서 실제로 처리됐는지 확인한다고 PR에 적었다. `latest`라서 첫 배포 때 새 앱이 파티션을 배정받기 전에 들어온 메시지는 읽지 않습니다

### 도메인별 주의

| 도메인 | 주의 |
|---|---|
| recommend | `applyIfNewer`에서 값 검사보다 `occurredAt` 비교를 먼저 하도록 바꿉니다 |
| settlement | 처리가 늦으면 구매확정분이 다음 달 정산으로 밀립니다. 정산 스케줄러의 실행 조건이 먼저 정해져야 합니다 |
| product | 주문 이벤트를 받는 두 클래스를 `kidly.order.events` 리스너 하나로 합칩니다 |
| payment | 가입 직후 지갑이 아직 없을 수 있습니다(`WALLET_NOT_FOUND`) |
| order | 결제 마감 만료 스케줄러가 lag을 확인한 뒤 실행해야 합니다(정상 주문의 잘못된 만료 방지). 결제 완료 반영은 자기 트랜잭션으로 처리합니다 |
| 화면 | 응답 직후 GET은 중간 상태를 볼 수 있습니다. 판매자 승인 직후 토큰 재발급, 자녀 등록 직후 주문처럼 다른 도메인 결과에 기대는 화면은 그 리스너를 옮길 때 확인합니다 |

### 마무리

- ArchUnit 규칙을 넣습니다. `@DomainEventListener`는 자기 도메인 이벤트만 받습니다(member ← `seller.changed`는 예외 목록에 이름으로 적습니다). 기존 리스너 위치 규칙(예: `OrderArchitectureTest`)의 검사 대상에 `@KafkaListener`를 더합니다.
- member는 서비스 분리 때 Gateway가 받을 토큰 무효화 이벤트를 `kidly.member.events`로 보내는 것까지 만들어 둡니다.

### 서비스로 떼어 내기 전

- 그 도메인이 받는 이벤트와 보내는 이벤트가 모두 Kafka로 옮겨졌는지 확인합니다. Spring 이벤트는 받는 쪽이 없어도 오류 없이 버려집니다.
- 떼어 낸 서비스의 Kafka 접속 방법을 인프라 담당과 정합니다. 지금은 같은 compose 안에서 `kafka:9092`로만 접속할 수 있습니다.
- 토픽, 키, 메시지 모양, 그룹 이름은 바꾸지 않습니다.

---

## 6. 미정

| 항목 | 현재 상태 |
|---|---|
| 스케줄러의 lag 확인 방법 | order, settlement가 정합니다. 결제 마감 만료는 1분마다 실행되므로 한 번 건너뛰어도 괜찮지만, 정산은 매월 1일에 한 번만 실행되므로 건너뛰면 다음 실행이 한 달 뒤가 됩니다 |
| DLT 재처리 코드 · 절차 | 별도 consumer로 다시 처리한다는 원칙만 정했습니다. 받는 도메인이 DLT에 메시지가 처음 쌓일 때 정합니다 |

---

## 부록. 팀 합의 후 반영한 것

| 항목 | 반영 내용 |
|---|---|
| 토픽 | 보내는 도메인마다 하나(`kidly.{보내는도메인}.events`)입니다. 재고 이벤트는 `kidly.product.events`, 구매확정은 `kidly.order.events`, 자녀 변경은 `kidly.childprofile.events`에 들어갑니다 |
| 읽기 실패 | 모르는 enum 값처럼 읽지 못한 메시지는 재시도 없이 DLT로 보내고 Slack으로 알립니다 |
| 읽기 시작 위치 | `auto-offset-reset: latest`입니다. 첫 배포 때 오프셋을 맞추는 명령을 쓰지 않습니다 |
| `max.block.ms` | 앱은 기본값(60초)입니다. 문제가 생기면 그때 바꿉니다 |
| 오프셋 보존 기간 | 기본값(7일)입니다 |
| 재시도 · 트랜잭션 | 재시도 정책과 리스너의 `@Transactional`은 각 도메인이 정합니다 |
| 테스트 | Kafka 컨테이너는 `@KafkaIntegrationTest`에서만 띄우고, 나머지 테스트는 보내기 스위치로 Kafka 보내기를 끕니다. 결과는 `support.AsyncWait`로 기다립니다 |
| ArchUnit | 모든 받는 쪽을 옮긴 뒤 마무리 단계에 넣습니다 |
| DLT 목적지 | DLT 전송기의 기본 목적지(`{토픽}-dlt`)를 쓰지 않고 `{토픽}.{받는도메인}.dlt`, 파티션 `-1`로 지정합니다 |
| 전송 실패 로그 | 앱은 Boot 기본 템플릿의 실패를 토픽 · 키 · `eventType` · `eventId`로 남기고 본문은 빼며, 직접 만든 템플릿은 기본 실패 로그를 끄고 실패를 직접 남깁니다 |
