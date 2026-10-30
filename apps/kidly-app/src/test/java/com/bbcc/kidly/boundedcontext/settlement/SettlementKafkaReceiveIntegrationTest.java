package com.bbcc.kidly.boundedcontext.settlement;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.shared.payment.domain.PayoutFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정산이 구매확정 · 지급 결과를 Kafka로 받아 Spring 이벤트로 받았을 때와 같이 처리하는지 실제 Kafka로 본다.
 * 테스트 설정은 스위치를 꺼 다른 테스트가 Spring 경로로 돌게 하므로, 여기서만 정산 리스너를 직접 켜고 끈다.
 * 메시지는 보내는 쪽과 같은 모양(키 = correlationId, 헤더 eventType · eventId, 본문 = 공통 칸 7개 + data)으로 직접 넣는다.
 * 앞 테스트가 남긴 주문 · 결제 메시지도 함께 읽으므로, 이 테스트의 값은 무작위 ID로 골라 본다.
 * 정산서 ID는 테이블을 비울 때 다시 1부터 시작해 다른 테스트와 겹치므로, DLT는 이 테스트가 보낸 eventId로 골라 본다.
 */
@KafkaIntegrationTest
class SettlementKafkaReceiveIntegrationTest {

    private static final String ORDER_TOPIC = "kidly.order.events";
    private static final String PAYMENT_TOPIC = "kidly.payment.events";
    private static final String ORDER_DLT = "kidly.order.events.settlement.dlt";
    private static final String PAYMENT_DLT = "kidly.payment.events.settlement.dlt";
    private static final String GROUP = "kidly.settlement";
    private static final List<String> LISTENER_IDS =
        List.of("settlementOrderKafkaListener", "settlementPaymentKafkaListener");
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

    @Autowired
    private SettlementRepository settlementRepository;

    private final long orderId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final long orderItemId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final long sellerId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

    private final List<MessageListenerContainer> listeners = new ArrayList<>();
    private final Set<String> sentEventIds = new HashSet<>();

    // 정산 팩토리는 처음 붙을 때 earliest로 읽지만, 이 그룹은 앞 테스트에서 이미 커밋한 위치부터 읽는다.
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
    @DisplayName("order-item.purchase-confirmed를 받으면 이벤트 값으로 정산 항목을 쌓는다")
    void purchaseConfirmed() throws Exception {
        OrderItemPurchaseConfirmedEvent event = purchaseConfirmed(orderItemId);

        send(ORDER_TOPIC, orderId, event.eventType(), envelope(event));

        AsyncWait.until(TIMEOUT, () -> itemCount(orderItemId) == 1);
        assertThat(jdbcTemplate.queryForMap("select order_id, seller_id, sales_amount, status "
            + "from settlement.settlement_item where order_item_id = ?", orderItemId))
            .containsEntry("order_id", orderId)
            .containsEntry("seller_id", sellerId)
            .containsEntry("sales_amount", 50_000L)
            .containsEntry("status", "PENDING");
    }

    @Test
    @DisplayName("같은 구매확정을 두 번 받아도 정산 항목은 1건이다")
    void purchaseConfirmedOnce() throws Exception {
        OrderItemPurchaseConfirmedEvent event = purchaseConfirmed(orderItemId);
        String body = envelope(event);

        send(ORDER_TOPIC, orderId, event.eventType(), body);
        RecordMetadata second = send(ORDER_TOPIC, orderId, event.eventType(), body);

        awaitProcessed(ORDER_TOPIC, second);
        assertThat(itemCount(orderItemId)).isEqualTo(1);
        assertThat(deadLetters(ORDER_DLT)).isEmpty();
    }

    @Test
    @DisplayName("payment.payout-completed를 받으면 정산서가 지급 완료가 된다")
    void payoutCompleted() throws Exception {
        long settlementId = saveSettlement();
        PaymentPayoutCompletedEvent event = completed(settlementId, sellerId, 90_000L);

        send(PAYMENT_TOPIC, settlementId, event.eventType(), envelope(event));

        AsyncWait.until(TIMEOUT, () -> "PAID".equals(settlementStatus(settlementId)));
    }

    @Test
    @DisplayName("payment.payout-failed를 받으면 정산서가 지급 실패가 된다")
    void payoutFailed() throws Exception {
        long settlementId = saveSettlement();
        PaymentPayoutFailedEvent event = new PaymentPayoutFailedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentPayoutFailedEvent.Data(settlementId, sellerId, 90_000L, PayoutFailureCode.WALLET_NOT_FOUND,
                LocalDateTime.now()));

        send(PAYMENT_TOPIC, settlementId, event.eventType(), envelope(event));

        AsyncWait.until(TIMEOUT, () -> "FAILED".equals(settlementStatus(settlementId)));
    }

    @Test
    @DisplayName("정산서가 없거나 금액이 다른 지급 결과는 반영하지 않고 재시도 없이 DLT로 보낸다")
    void deadLetterMismatchedPayout() throws Exception {
        long settlementId = saveSettlement();
        long unknownSettlementId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        PaymentPayoutCompletedEvent wrongAmount = completed(settlementId, sellerId, 90_001L);
        PaymentPayoutCompletedEvent unknown = completed(unknownSettlementId, sellerId, 90_000L);

        RecordMetadata wrongAmountSent = send(PAYMENT_TOPIC, settlementId, wrongAmount.eventType(),
            envelope(wrongAmount));
        RecordMetadata unknownSent = send(PAYMENT_TOPIC, unknownSettlementId, unknown.eventType(), envelope(unknown));

        AsyncWait.until(TIMEOUT, () -> deadLetters(PAYMENT_DLT).size() == 2);
        Map<String, Long> sentAt = Map.of(String.valueOf(settlementId), wrongAmountSent.timestamp(),
            String.valueOf(unknownSettlementId), unknownSent.timestamp());
        assertThat(deadLetters(PAYMENT_DLT)).allSatisfy(deadLetter ->
            assertThat(deadLetter.timestamp() - sentAt.get(deadLetter.key())).isLessThan(RETRY_BACKOFF_TOTAL_MS));
        assertThat(settlementStatus(settlementId)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("주문 토픽에서 정산이 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipOtherOrderEventType() throws Exception {
        send(ORDER_TOPIC, orderId, "order.created", "정산이 받지 않는 종류라 읽지 않는 본문");
        // 키(주문 ID)가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        OrderItemPurchaseConfirmedEvent event = purchaseConfirmed(orderItemId);
        send(ORDER_TOPIC, orderId, event.eventType(), envelope(event));

        AsyncWait.until(TIMEOUT, () -> itemCount(orderItemId) == 1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from settlement.settlement_item where order_id = ?",
            Long.class, orderId)).isEqualTo(1L);
        assertThat(deadLetters(ORDER_DLT)).isEmpty();
    }

    @Test
    @DisplayName("결제 토픽에서 정산이 받지 않는 종류는 건너뛰고 DLT로 보내지 않으며, 뒤 메시지를 처리한다")
    void skipOtherPaymentEventType() throws Exception {
        long settlementId = saveSettlement();
        send(PAYMENT_TOPIC, settlementId, "payment.payout-requested", "정산이 받지 않는 종류라 읽지 않는 본문");
        // 키(정산서 ID)가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        PaymentPayoutCompletedEvent event = completed(settlementId, sellerId, 90_000L);
        send(PAYMENT_TOPIC, settlementId, event.eventType(), envelope(event));

        AsyncWait.until(TIMEOUT, () -> "PAID".equals(settlementStatus(settlementId)));
        assertThat(deadLetters(PAYMENT_DLT)).isEmpty();
    }

    private OrderItemPurchaseConfirmedEvent purchaseConfirmed(long itemId) {
        return new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderItemPurchaseConfirmedEvent.Data(itemId, orderId, 2L, sellerId, null, 3L, 4L, "110", 1, 50_000L,
                LocalDateTime.of(2026, 10, 1, 10, 0)));
    }

    private static PaymentPayoutCompletedEvent completed(long settlementId, long sellerId, long amount) {
        return new PaymentPayoutCompletedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentPayoutCompletedEvent.Data(settlementId, sellerId, amount, 1L, LocalDateTime.now()));
    }

    // 판매 100,000원 · 수수료 10% → 정산금액 90,000원. 지급을 요청한(COMPLETED) 정산서다
    private long saveSettlement() {
        return settlementRepository.save(SettlementFixture.settlement(sellerId, SettlementFixture.PERIOD_START,
            SettlementFixture.PERIOD_END)).getId();
    }

    private long itemCount(long itemId) {
        return jdbcTemplate.queryForObject("select count(*) from settlement.settlement_item where order_item_id = ?",
            Long.class, itemId);
    }

    private String settlementStatus(long settlementId) {
        return jdbcTemplate.queryForObject("select status from settlement.settlement where id = ?", String.class,
            settlementId);
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
        String eventId = UUID.randomUUID().toString();
        sentEventIds.add(eventId);
        record.headers().add("eventId", eventId.getBytes(UTF_8));
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    // 결과가 바뀌지 않는 메시지는 처리했는지 결과로 알 수 없다. 한 건씩 처리 완료를 커밋하므로, 정산 그룹의 커밋 위치가 그 메시지를 지나면 처리한 것이다
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
            throw new IllegalStateException("정산 그룹의 커밋 위치를 읽지 못했습니다", e);
        }
    }

    // DLT를 처음부터 지금 끝까지 읽어 이 테스트가 보낸 메시지만 돌려준다. 다른 테스트가 넣은 DLT 메시지도 있다
    private List<ConsumerRecord<String, String>> deadLetters(String deadLetterTopic) {
        TopicPartition partition = new TopicPartition(deadLetterTopic, 0);
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
            return records.stream().filter(record -> sentEventIds.contains(
                new String(record.headers().lastHeader("eventId").value(), UTF_8))).toList();
        }
    }
}
