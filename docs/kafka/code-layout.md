# 코드 배치

| 위치 | 담는 것 | 담지 않는 것 |
|---|---|---|
| `shared` | 이벤트 record(바뀌지 않음, 필드는 추가만) | Kafka 코드 |
| `common` | `EventPublisher`(Kafka를 모른 채 Spring 이벤트만 발행) | Kafka 코드와 설정 |
| 보내는 `domains/*` | `out`에 토픽 선언(`NewTopic`)과 커밋 뒤 발행 빈 | 다른 도메인 토픽 |
| 받는 `domains/*` | 자기 리스너 설정(컨테이너 팩토리, 에러 처리기, DLT `NewTopic`)과 `@KafkaListener` 리스너. 리스너 패키지는 지금 위치 그대로(대부분 `in`, payment는 `app`) | `spring.kafka.*` yml |
| `apps/*-service` | 서비스 공용 Kafka 설정, Kafka 테스트 지원(`@KafkaIntegrationTest`, `support.AsyncWait`) | 도메인별 Kafka 설정 |

## 예시 코드와 맞춰야 하는 것

아래 코드는 모양을 보여 주는 예시입니다. 클래스 이름, 중계 빈과 전송 클래스를 나눌지, 전송 스레드, 리스너 안에서 종류를 나누는 방법은 각 도메인이 정합니다. 예를 들어 회원 · 판매자 · 자녀는 중계 빈과 전송 클래스를 나누고 전용 스레드에서 보내고, 결제 · 상품은 아웃박스에 저장한 뒤 릴레이가 보냅니다.

다만 다른 도메인의 코드도 쉽게 알아볼 수 있도록, 예시의 구조와 이름 짓는 방식을 비슷하게 유지하기를 권장합니다. 토픽과 보내는 도메인 이름을 상수로 두기, 커밋 뒤에 보내는 중계 빈, 토픽마다 리스너 클래스 하나와 그 안의 `switch` 분기가 여기에 해당합니다. 권장이며 강제하지는 않습니다.

모든 도메인이 똑같이 맞춰야 하는 것은 1절의 팀 규칙입니다. 다른 도메인이 받는 메시지와 이름에 관한 규칙이기 때문입니다. 토픽 이름, 키, 본문(공통 칸 7개 + `data`), 헤더 두 개, 커밋 뒤에 보내기, 보내기 스위치, 구독 그룹과 DLT 이름이 여기에 해당합니다.

## 보내는 쪽

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

## 받는 쪽

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

## 받는 쪽 리스너 설정

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
