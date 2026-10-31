package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.order.support.KafkaIntegrationTest;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * 주문이 kidly.childprofile.events의 child.changed를 Kafka로 받는다(#541). 테스트가 보내는 쪽 규칙대로 메시지를 직접 넣는다
 * (Spring 이벤트를 발행하지 않으므로 Kafka로만 들어온다). 리스너는 이 테스트 안에서만 켜고, 자녀 ID는 테스트마다 새로 만든다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OrderChildProfileEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.childprofile.events";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 30, 9, 0);
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

    private final Long childId = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    private OrderKafkaTestClient kafkaClient;
    private MessageListenerContainer listener;

    @BeforeEach
    void startListener() {
        kafkaClient = new OrderKafkaTestClient(kafka, jsonMapper);
        listener = kafkaClient.start(guard, ChildProfileEventKafkaListener.ID, TOPIC);
    }

    @AfterEach
    void stopListener() {
        listener.stop();
    }

    @Test
    @DisplayName("child.changed(등록 → 삭제)를 Kafka로 받은 자녀 복사본이 같은 값을 Spring 이벤트로 받은 것과 같다")
    void sameAsSpringEvent() {
        Long springChildId = childId + 1;
        eventDelivery.deliver(childChanged(springChildId, OCCURRED_AT, false));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, childChanged(childId, OCCURRED_AT, false)));

        assertThat(storedChild(childId)).isNotNull().isEqualTo(storedChild(springChildId));

        eventDelivery.deliver(childChanged(springChildId, OCCURRED_AT.plusMinutes(1), true));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, childChanged(childId, OCCURRED_AT.plusMinutes(1), true)));

        assertThat(storedChild(childId)).containsEntry("is_deleted", true).isEqualTo(storedChild(springChildId));
    }

    @Test
    @DisplayName("같은 메시지를 두 번 받아도 복사본은 한 번 받은 것과 같고 DLT로 보내지 않는다")
    void duplicateAppliedOnce() {
        var event = childChanged(childId, OCCURRED_AT, false);
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));
        Map<String, Object> first = jdbcTemplate.queryForMap(
            "select * from orders.replica_child where child_id = ?", childId);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        assertThat(jdbcTemplate.queryForList("select * from orders.replica_child where child_id = ?", childId))
            .containsExactly(first);
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("모르는 성별 값은 재시도 없이 DLT에 1건 보내고, 로그에 본문 값은 없다")
    void unknownEnumToDeadLetter(CapturedOutput output) {
        String marker = "NEW_GENDER_" + UUID.randomUUID().toString().replace("-", "");
        var event = childChanged(childId, OCCURRED_AT, false);
        Map<String, Object> body = kafkaClient.envelopeFields(event);
        body.put("data", Map.of("childId", childId, "memberId", 1, "heightCm", 115.0, "weightKg", 20.0,
            "gender", marker, "birthDate", "2019-01-01", "deleted", false));
        String json = kafkaClient.toJson(body);
        String eventId = event.eventId().toString();

        RecordMetadata sent = kafkaClient.send(TOPIC, String.valueOf(childId), event.eventType(), eventId, json);

        AsyncWait.until(OrderKafkaTestClient.TIMEOUT, () -> !kafkaClient.deadLetters(TOPIC, eventId).isEmpty());
        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.partition()).isZero();
            assertThat(deadLetter.value()).isEqualTo(json);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
        assertThat(storedChild(childId)).isNull();
        assertThat(output.getOut()).contains("eventId=" + eventId).doesNotContain(marker);
    }

    private Map<String, Object> storedChild(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select member_id, name, height_cm, weight_kg, "
            + "body_type, is_deleted, event_occurred_at from orders.replica_child where child_id = ?", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static ChildChangedEvent childChanged(Long id, LocalDateTime occurredAt, boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), occurredAt, new ChildChangedEvent.Data(id, 1L,
            new BigDecimal("115.5"), new BigDecimal("20.0"), Gender.MALE, LocalDate.of(2019, 1, 1), BodyType.NORMAL,
            PreferredFit.LOOSE, deleted));
    }
}
