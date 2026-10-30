package com.bbcc.kidly.boundedcontext.payment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제가 회원 · 주문 이벤트를 Kafka로 받아 Spring 이벤트로 받았을 때와 같이 처리하는지 실제 Kafka로 본다(#593).
 * 테스트 설정은 스위치를 꺼 다른 테스트가 Spring 경로로 돌게 하므로, 여기서만 결제 리스너를 직접 켜고 끈다.
 * 메시지는 보내는 쪽과 같은 모양(키 = correlationId, 헤더 eventType · eventId, 본문 = 공통 칸 7개 + data)으로 직접 넣는다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class PaymentKafkaReceiveIntegrationTest {

    private static final String MEMBER_TOPIC = "kidly.member.events";
    private static final String ORDER_TOPIC = "kidly.order.events";
    private static final String ORDER_DLT = "kidly.order.events.payment.dlt";
    private static final String GROUP = "kidly.payment";
    private static final List<String> LISTENER_IDS = List.of("paymentMemberEventListener", "paymentOrderEventListener");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final long memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final long orderId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

    private final List<MessageListenerContainer> listeners = new ArrayList<>();

    // 결제 팩토리는 처음 붙을 때 earliest로 읽지만, 이 그룹은 앞 테스트에서 이미 커밋한 위치부터 읽는다.
    // 파티션을 배정받은 뒤에 보내야 이 테스트의 메시지를 놓치지 않는다
    @BeforeEach
    void startListeners() {
        for (String id : LISTENER_IDS) {
            MessageListenerContainer listener = registry.getListenerContainer(id);
            listener.start();
            listeners.add(listener);
        }
        AsyncWait.until(TIMEOUT, () -> listeners.stream().noneMatch(l -> l.getAssignedPartitions().isEmpty()));
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListeners() {
        listeners.forEach(MessageListenerContainer::stop);
    }

    @Test
    @DisplayName("member.registered를 받으면 잔액 0인 지갑을 만든다")
    void memberRegistered() throws Exception {
        send(MEMBER_TOPIC, memberId, "member.registered", envelope(new MemberRegisteredEvent(UUID.randomUUID(),
            LocalDateTime.now(), new MemberRegisteredEvent.Data(memberId, "buyer@kidly.test"))));

        AsyncWait.until(TIMEOUT, () -> balance() != null);
        assertThat(balance()).isZero();
    }

    @Test
    @DisplayName("order.payment-requested를 받으면 예치금으로 결제하고, 같은 메시지를 두 번 받아도 한 번만 결제한다")
    void paymentRequestedOnce() throws Exception {
        createWallet(20_000L);
        String body = envelope(paymentRequested(15_000L));

        send(ORDER_TOPIC, orderId, "order.payment-requested", body);
        RecordMetadata second = send(ORDER_TOPIC, orderId, "order.payment-requested", body);

        awaitProcessed(ORDER_TOPIC, second);
        assertThat(paymentStatuses()).containsExactly("COMPLETED");
        assertThat(balance()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("결제된 주문의 order.cancelled를 받으면 전액 환불한다")
    void cancelledAfterPayment() throws Exception {
        createWallet(20_000L);
        send(ORDER_TOPIC, orderId, "order.payment-requested", envelope(paymentRequested(15_000L)));
        // 키(주문 ID)가 같아 같은 파티션이므로 결제 요청 뒤에 취소를 처리한다
        send(ORDER_TOPIC, orderId, "order.cancelled", envelope(orderCancelled()));

        AsyncWait.until(TIMEOUT, () -> paymentStatuses().equals(List.of("REFUNDED")));
        assertThat(balance()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("결제가 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipOtherEventType() throws Exception {
        createWallet(20_000L);
        send(ORDER_TOPIC, orderId, "order.created", "결제가 받지 않는 종류라 읽지 않는 본문");
        // 키(주문 ID)가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        send(ORDER_TOPIC, orderId, "order.payment-requested", envelope(paymentRequested(15_000L)));

        AsyncWait.until(TIMEOUT, () -> paymentStatuses().equals(List.of("COMPLETED")));
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @DisplayName("본문을 읽지 못하면 재시도 없이 DLT로 보내고, 로그에 본문을 남기지 않는다")
    void deadLetterUnreadableBody(CapturedOutput output) throws Exception {
        String secret = "본문-" + UUID.randomUUID();
        String body = "{\"data\":{\"orderId\":\"숫자 아님\",\"memo\":\"" + secret + "\"}}";

        RecordMetadata sent = send(ORDER_TOPIC, orderId, "order.payment-requested", body);

        AsyncWait.until(TIMEOUT, () -> !deadLetters().isEmpty());
        assertThat(deadLetters()).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
        assertThat(output.getOut()).contains("key=" + orderId).doesNotContain(secret);
        assertThat(paymentStatuses()).isEmpty();
    }

    private OrderPaymentRequestedEvent paymentRequested(long amount) {
        return new OrderPaymentRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderPaymentRequestedEvent.Data(orderId, memberId, amount,
                List.of(new OrderPaymentRequestedEvent.Item(orderId * 10, amount))));
    }

    private OrderCancelledEvent orderCancelled() {
        return new OrderCancelledEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCancelledEvent.Data(orderId, memberId, "PAYMENT_EXPIRED",
                List.of(new OrderCancelledEvent.Item(orderId * 10, 1L, 1))));
    }

    private void createWallet(long balance) {
        jdbcTemplate.update("insert into payment.wallet (member_id, balance, created_at, updated_at) "
            + "values (?, ?, now(), now())", memberId, balance);
    }

    private Long balance() {
        List<Long> balances = jdbcTemplate.queryForList(
            "select balance from payment.wallet where member_id = ?", Long.class, memberId);
        return balances.isEmpty() ? null : balances.getFirst();
    }

    private List<String> paymentStatuses() {
        return jdbcTemplate.queryForList("select status from payment.payment where order_id = ?", String.class,
            orderId);
    }

    // 보내는 쪽 본문과 같은 모양: 공통 칸 7개 + data. 이벤트 record에 없는 칸(eventType 등)도 들어 있다
    private String envelope(DomainEvent event) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = jsonMapper.convertValue(event, Map.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", fields.get("eventId"));
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", fields.get("occurredAt"));
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", fields.get("data"));
        return jsonMapper.writeValueAsString(body);
    }

    private RecordMetadata send(String topic, long key, String eventType, String body) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, String.valueOf(key), body);
        record.headers().add("eventId", UUID.randomUUID().toString().getBytes(UTF_8));
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    // 결과가 바뀌지 않는 메시지는 처리했는지 결과로 알 수 없다. 한 건씩 처리 완료를 커밋하므로, 결제 그룹의 커밋 위치가 그 메시지를 지나면 처리한 것이다
    private void awaitProcessed(String topic, RecordMetadata sent) {
        TopicPartition partition = new TopicPartition(topic, sent.partition());
        Map<String, Object> config = Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (Admin admin = Admin.create(config)) {
            AsyncWait.until(TIMEOUT, () -> committedOffset(admin, partition) > sent.offset());
        }
    }

    private static long committedOffset(Admin admin, TopicPartition partition) {
        try {
            var committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata()
                .get(10, TimeUnit.SECONDS).get(partition);
            return committed == null ? -1 : committed.offset();
        } catch (Exception e) {
            throw new IllegalStateException("결제 그룹의 커밋 위치를 읽지 못했습니다", e);
        }
    }

    // DLT를 처음부터 지금 끝까지 읽어 이 테스트의 키만 돌려준다. 다른 테스트가 넣은 DLT 메시지도 있다
    private List<ConsumerRecord<String, String>> deadLetters() {
        TopicPartition partition = new TopicPartition(ORDER_DLT, 0);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            long end = consumer.endOffsets(List.of(partition)).get(partition);
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            while (consumer.position(partition) < end) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records.stream().filter(record -> String.valueOf(orderId).equals(record.key())).toList();
        }
    }
}
