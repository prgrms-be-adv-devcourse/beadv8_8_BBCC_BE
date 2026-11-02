package com.bbcc.kidly.boundedcontext.review.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.KafkaIntegrationTest;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import java.time.LocalDateTime;
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
 * 커뮤니티가 kidly.order.events의 order-item.purchase-confirmed를 그룹 kidly.community로 받는다. 테스트가 보내는 쪽 규칙대로
 * 메시지를 직접 넣는다(키 = 주문 ID). 처리 실패는 한 주문상품 행만 막는 CHECK 제약(NOT VALID)으로 만든다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class CommunityOrderEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.order.events";
    private static final String BREAK_ELIGIBILITY = "test_break_community_review_eligibility";
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;
    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.now().minusMinutes(5);

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private CommunityKafkaListenerTestGuard guard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CommunityKafkaTestClient kafkaClient;
    private MessageListenerContainer listener;

    @BeforeEach
    void startListener() {
        kafkaClient = new CommunityKafkaTestClient(kafka, jsonMapper);
        listener = kafkaClient.start(guard, CommunityOrderEventKafkaListener.ID, TOPIC);
        jdbcTemplate.update("delete from community.review_eligibility");
    }

    @AfterEach
    void stopListener() {
        listener.stop();
        jdbcTemplate.execute("alter table community.review_eligibility drop constraint if exists " + BREAK_ELIGIBILITY);
    }

    @Test
    @DisplayName("order-item.purchase-confirmed를 받아 자격을 저장하고, 두 번 받아도 1건이다")
    void purchaseConfirmedStoredOnce() {
        long orderItemId = newId();
        var event = confirmed(orderItemId, 13L);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        assertThat(rowCount(orderItemId)).isEqualTo(1);
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("커뮤니티가 받지 않는 주문 이벤트는 건너뛰고 DLT로 보내지 않는다")
    void skipsOtherOrderEvents() {
        String eventId = CommunityKafkaTestClient.newEventId();

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, "1", "order.created", eventId, "커뮤니티가 읽지 않는 본문"));

        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).isEmpty();
    }

    @Test
    @DisplayName("필수값(회원 ID)이 없으면 WARN 한 줄 후 무시하고 DLT로 보내지 않는다")
    void missingRequiredValueIgnored(CapturedOutput output) {
        long orderItemId = newId();
        var event = confirmed(orderItemId, null);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        assertThat(rowCount(orderItemId)).isZero();
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
        assertThat(output.getOut()).contains("구매확정 이벤트 무시", "orderItemId=" + orderItemId,
            "reason=missing-member-id");
    }

    @Test
    @DisplayName("저장이 실패하면 1초 간격 3번 재시도한 뒤 kidly.order.events.community.dlt(파티션 0)에 1건 보낸다")
    void processingFailureToDeadLetter() {
        long orderItemId = newId();
        jdbcTemplate.execute("alter table community.review_eligibility add constraint " + BREAK_ELIGIBILITY
            + " check (order_item_id <> " + orderItemId + ") not valid");
        var event = confirmed(orderItemId, 13L);

        RecordMetadata sent = kafkaClient.send(TOPIC, event);

        String eventId = event.eventId().toString();
        AsyncWait.until(CommunityKafkaTestClient.TIMEOUT, () -> !kafkaClient.deadLetters(TOPIC, eventId).isEmpty());
        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.partition()).isZero();
            assertThat(deadLetter.key()).isEqualTo(event.correlationId());
            assertThat(deadLetter.timestamp() - sent.timestamp()).isGreaterThanOrEqualTo(RETRY_BACKOFF_TOTAL_MS);
        });
        assertThat(rowCount(orderItemId)).isZero();
    }

    @Test
    @DisplayName("모르는 필드가 더 있어도 읽는다")
    void ignoresUnknownFields() {
        long orderItemId = newId();
        var event = confirmed(orderItemId, 13L);
        Map<String, Object> body = kafkaClient.envelopeFields(event);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = jsonMapper.convertValue(event.data(), Map.class);
        data.put("newField", "새 값");
        body.put("data", data);
        body.put("traceId", "새 공통 칸");

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event.correlationId(), event.eventType(),
            event.eventId().toString(), kafkaClient.toJson(body)));

        assertThat(rowCount(orderItemId)).isEqualTo(1);
    }

    private int rowCount(long orderItemId) {
        return jdbcTemplate.queryForObject("select count(*) from community.review_eligibility where order_item_id = ?",
            Integer.class, orderItemId);
    }

    private static long newId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    }

    private static OrderItemPurchaseConfirmedEvent confirmed(long orderItemId, Long memberId) {
        return new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), CONFIRMED_AT.plusSeconds(1),
            new OrderItemPurchaseConfirmedEvent.Data(orderItemId, orderItemId / 10, memberId, 20L, null, 14L, 15L,
                "110", 1, 25_000L, CONFIRMED_AT));
    }
}
