package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.KafkaIntegrationTest;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문이 kidly.seller.events의 seller.changed를 Kafka로 받는다(#541). 테스트가 보내는 쪽 규칙대로 메시지를 직접 넣는다
 * (Spring 이벤트를 발행하지 않으므로 Kafka로만 들어온다). 리스너는 이 테스트 안에서만 켜고, 판매자 ID는 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OrderSellerEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.seller.events";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 30, 9, 0);
    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private OrderKafkaListenerTestGuard guard;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Long sellerId = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    private OrderKafkaTestClient kafkaClient;
    private MessageListenerContainer listener;

    @BeforeEach
    void startListener() {
        kafkaClient = new OrderKafkaTestClient(kafka, jsonMapper);
        listener = kafkaClient.start(guard, SellerEventKafkaListener.ID, TOPIC);
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListener() {
        listener.stop();
    }

    @Test
    @DisplayName("seller.changed를 Kafka로 받은 판매자 복사본이 같은 값을 Spring 이벤트로 받은 것과 같다")
    void sameAsSpringEvent() {
        Long springSellerId = sellerId + 1;
        eventDelivery.deliver(sellerChanged(springSellerId, "키들리샵", SellerStatus.ACTIVE));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, sellerChanged(sellerId, "키들리샵", SellerStatus.ACTIVE)));

        assertThat(storedSeller(sellerId)).isNotNull().isEqualTo(storedSeller(springSellerId));
    }

    @Test
    @DisplayName("같은 메시지를 두 번 받아도 복사본은 한 번 받은 것과 같고 DLT로 보내지 않는다")
    void duplicateAppliedOnce() {
        var event = sellerChanged(sellerId, "키들리샵", SellerStatus.ACTIVE);
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));
        Map<String, Object> first = jdbcTemplate.queryForMap(
            "select * from orders.replica_seller where seller_id = ?", sellerId);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        assertThat(jdbcTemplate.queryForList("select * from orders.replica_seller where seller_id = ?", sellerId))
            .containsExactly(first);
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("공통 칸 · data에 모르는 필드가 더 있어도 무시하고 처리한다")
    void ignoresUnknownFields() {
        var event = sellerChanged(sellerId, "키들리샵", SellerStatus.ACTIVE);
        Map<String, Object> body = kafkaClient.envelopeFields(event);
        body.put("traceId", "새 공통 칸");
        body.put("data", Map.of("sellerId", sellerId, "businessName", "키들리샵", "status", "ACTIVE",
            "newField", List.of(1, 2)));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, String.valueOf(sellerId), event.eventType(),
            event.eventId().toString(), kafkaClient.toJson(body)));

        assertThat(storedSeller(sellerId)).containsEntry("business_name", "키들리샵");
    }

    @Test
    @DisplayName("모르는 판매자 상태는 재시도 없이 DLT에 1건 보내고, 로그에는 eventType · eventId · 키만 남고 본문 값은 없다")
    void unknownEnumToDeadLetter(CapturedOutput output) {
        String marker = "NEW_STATUS_" + UUID.randomUUID().toString().replace("-", "");
        String eventId = OrderKafkaTestClient.newEventId();
        String body = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"seller.changed\",\"schemaVersion\":1,"
            + "\"occurredAt\":\"2026-10-30T09:00:00\",\"producer\":\"seller\",\"aggregateId\":\"" + sellerId + "\","
            + "\"correlationId\":\"" + sellerId + "\",\"data\":{\"sellerId\":" + sellerId
            + ",\"businessName\":\"키들리샵\",\"status\":\"" + marker + "\"}}";

        RecordMetadata sent = kafkaClient.send(TOPIC, String.valueOf(sellerId), "seller.changed", eventId, body);

        AsyncWait.until(OrderKafkaTestClient.TIMEOUT, () -> !kafkaClient.deadLetters(TOPIC, eventId).isEmpty());
        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.partition()).isZero();
            assertThat(deadLetter.key()).isEqualTo(String.valueOf(sellerId));
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.replica_seller where seller_id = ?",
            Long.class, sellerId)).isZero();
        assertThat(output.getOut()).contains("eventId=" + eventId, "key=" + sellerId).doesNotContain(marker);
    }

    private Map<String, Object> storedSeller(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "select business_name, status, event_occurred_at from orders.replica_seller where seller_id = ?", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static SellerChangedEvent sellerChanged(Long id, String businessName, SellerStatus status) {
        return new SellerChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new SellerChangedEvent.Data(id, businessName, status));
    }
}
