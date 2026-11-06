package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.KafkaIntegrationTest;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문이 kidly.auth.events의 child-account.changed를 Kafka로 받아 자녀 계정 복사본을 만든다(#906). 테스트가 보내는 쪽(회원) 규칙대로
 * 메시지를 직접 넣는다. 리스너는 이 테스트 안에서만 켜고, 계정 ID는 테스트마다 새로 만든다.
 * 직접 처리(처리 빈 호출)와 결과가 같은지, 생성 · 삭제 · 순서 뒤바뀜 · 중복 · 삭제 뒤 예전 생성 무시를 본다.
 */
@KafkaIntegrationTest
class OrderChildAccountEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.auth.events";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 5, 21, 0);
    private static final Long CHILD_PROFILE_ID = 3L;
    private static final Long GUARDIAN_ID = 1L;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private OrderKafkaListenerTestGuard guard;

    @Autowired
    private ChildAccountEventListener directListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Long accountId = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE - 10);
    private OrderKafkaTestClient kafkaClient;
    private MessageListenerContainer listener;

    @BeforeEach
    void startListener() {
        kafkaClient = new OrderKafkaTestClient(kafka, jsonMapper);
        listener = kafkaClient.start(guard, AuthEventKafkaListener.ID, TOPIC);
    }

    @AfterEach
    void stopListener() {
        listener.stop();
    }

    @Test
    @DisplayName("생성 → 삭제를 Kafka로 받은 복사본이 처리 빈을 직접 부른 것과 같다")
    void sameAsDirectProcessing() {
        Long directId = accountId + 1;
        directListener.onChildAccountChanged(changed(directId, OCCURRED_AT, false));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, changed(accountId, OCCURRED_AT, false)));

        assertThat(stored(accountId)).containsEntry("child_profile_id", CHILD_PROFILE_ID)
            .containsEntry("guardian_id", GUARDIAN_ID).containsEntry("deleted_at", null)
            .isEqualTo(stored(directId));

        directListener.onChildAccountChanged(changed(directId, OCCURRED_AT.plusMinutes(1), true));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, changed(accountId, OCCURRED_AT.plusMinutes(1), true)));

        assertThat(stored(accountId)).containsEntry("deleted_at", Timestamp.valueOf(OCCURRED_AT.plusMinutes(1)))
            .isEqualTo(stored(directId));
    }

    @Test
    @DisplayName("같은 메시지를 두 번 받아도 한 번 받은 것과 같고 DLT로 보내지 않는다")
    void duplicateAppliedOnce() {
        var event = changed(accountId, OCCURRED_AT, false);
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));
        Map<String, Object> first = stored(accountId);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        assertThat(rows(accountId)).containsExactly(first);
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("삭제가 먼저 오면(순서 뒤바뀜) 삭제된 행으로 남고, 뒤에 온 예전 생성 · 다시 온 삭제는 무시한다")
    void deleteBeforeCreate() {
        var deletion = changed(accountId, OCCURRED_AT, true);
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, deletion));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, changed(accountId, OCCURRED_AT.minusMinutes(1), false)));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, changed(accountId, OCCURRED_AT.plusMinutes(1), false)));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, deletion));

        assertThat(rows(accountId)).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("deleted_at", Timestamp.valueOf(OCCURRED_AT))
            .containsEntry("event_occurred_at", Timestamp.valueOf(OCCURRED_AT)));
        assertThat(kafkaClient.deadLetters(TOPIC, deletion.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("주문이 받지 않는 종류(토큰 무효화)는 건너뛰고 DLT로 보내지 않는다")
    void skipsTokenRevoked() {
        String eventId = OrderKafkaTestClient.newEventId();
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, "1", "auth.tokens-revoked", eventId, "{\"data\":{}}"));

        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).isEmpty();
    }

    private Map<String, Object> stored(Long id) {
        List<Map<String, Object>> rows = rows(id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<Map<String, Object>> rows(Long id) {
        return jdbcTemplate.queryForList("select child_profile_id, guardian_id, deleted_at, event_occurred_at "
            + "from orders.replica_child_account where account_id = ?", id);
    }

    private static ChildAccountChangedEvent changed(Long id, LocalDateTime occurredAt, boolean deleted) {
        return new ChildAccountChangedEvent(UUID.randomUUID(), occurredAt,
            new ChildAccountChangedEvent.Data(id, CHILD_PROFILE_ID, GUARDIAN_ID, deleted));
    }
}
