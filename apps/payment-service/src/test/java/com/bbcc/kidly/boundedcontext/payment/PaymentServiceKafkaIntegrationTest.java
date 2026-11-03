package com.bbcc.kidly.boundedcontext.payment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentOutboxRelayService;
import com.bbcc.kidly.shared.event.DomainEvent;
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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제 서비스가 kidly-app과 같은 구독 그룹(kidly.payment)으로 Kafka에 붙어 받은 결제 요청을 처리하고,
 * 그 결과를 아웃박스 릴레이로 kidly.payment.events에 보내는지 본다.
 * 받는 종류마다의 처리와 보내는 형식은 kidly-app의 결제 Kafka 테스트가 확인하고, 여기서는 결제 요청 하나로 받기부터 보내기까지만 본다.
 */
@KafkaIntegrationTest
class PaymentServiceKafkaIntegrationTest {

    private static final String ORDER_TOPIC = "kidly.order.events";
    private static final String PAYMENT_TOPIC = "kidly.payment.events";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentOutboxRelayService relayService;

    // 같은 토픽에 다른 테스트의 메시지가 쌓여 있을 수 있으므로 회원 · 주문 ID를 새로 만든다
    private final long memberId = ThreadLocalRandom.current().nextLong(1, 1_000_000_000L);
    private final long orderId = ThreadLocalRandom.current().nextLong(1, 1_000_000_000L);

    private MessageListenerContainer listener;

    // 결제 팩토리는 처음 붙을 때 earliest로 읽지만, 파티션을 배정받은 뒤에 보내야 이 테스트의 메시지를 놓치지 않는다
    @BeforeEach
    void startListener() {
        listener = registry.getListenerContainer("paymentOrderEventListener");
        listener.start();
        AsyncWait.until(TIMEOUT, () -> !listener.getAssignedPartitions().isEmpty());
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListener() {
        listener.stop();
    }

    @Test
    @DisplayName("order.payment-requested를 결제 그룹으로 받아 결제하고, payment.completed를 주문 ID 키로 보낸다")
    void receivePaymentRequestedAndPublishCompleted() throws Exception {
        jdbcTemplate.update("insert into payment.wallet (member_id, balance, created_at, updated_at) "
            + "values (?, ?, now(), now())", memberId, 20_000L);

        send(ORDER_TOPIC, "order.payment-requested", new OrderPaymentRequestedEvent(UUID.randomUUID(),
            LocalDateTime.now(), new OrderPaymentRequestedEvent.Data(orderId, memberId, 15_000L,
            List.of(new OrderPaymentRequestedEvent.Item(orderId * 10, 15_000L)))));

        AsyncWait.until(TIMEOUT, () -> !paymentStatuses().isEmpty());
        assertThat(paymentStatuses()).containsExactly("COMPLETED");

        relayService.relay();
        ConsumerRecord<String, String> completed = awaitPaymentEvent("payment.completed");
        assertThat(completed.key()).isEqualTo(String.valueOf(orderId));
    }

    private List<String> paymentStatuses() {
        return jdbcTemplate.queryForList("select status from payment.payment where order_id = ?", String.class,
            orderId);
    }

    // 보내는 쪽과 같은 모양: 키 = correlationId, 헤더 eventType · eventId, 본문 = 공통 칸 7개 + data
    private void send(String topic, String eventType, DomainEvent event) throws Exception {
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

        var record = new ProducerRecord<>(topic, event.correlationId(), jsonMapper.writeValueAsString(body));
        record.headers().add("eventId", event.eventId().toString().getBytes(UTF_8));
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    // 다른 테스트의 결제 이벤트도 같은 토픽에 쌓이므로 키(주문 ID)와 eventType으로 이 테스트의 메시지를 찾는다
    private ConsumerRecord<String, String> awaitPaymentEvent(String eventType) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "payment-service-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(PAYMENT_TOPIC));
            AsyncWait.until(TIMEOUT, () -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    var header = record.headers().lastHeader("eventType");
                    if (String.valueOf(orderId).equals(record.key()) && header != null
                        && eventType.equals(new String(header.value(), UTF_8))) {
                        found.add(record);
                    }
                }
                return !found.isEmpty();
            });
        }
        return found.getFirst();
    }
}
