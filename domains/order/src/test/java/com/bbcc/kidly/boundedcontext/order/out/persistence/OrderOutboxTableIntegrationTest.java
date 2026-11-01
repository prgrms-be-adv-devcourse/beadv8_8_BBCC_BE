package com.bbcc.kidly.boundedcontext.order.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.out.messaging.OrderOutboxMessage;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 아웃박스 표(orders.order_outbox, #540)와 엔티티가 맞는지. 저장 · 보냄 기록이 칸마다 그대로 들어가고,
 * 이벤트 ID는 한 번만 저장되며, 릴레이 · 정리가 쓰는 부분 인덱스가 있다. 값은 DB에서 다시 읽어 비교한다.
 */
@IntegrationTest
class OrderOutboxTableIntegrationTest {

    private static final String PAYLOAD = "{\"eventId\":\"6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90\",\"data\":{}}";

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("저장하면 칸마다 값이 들어가고 보내지 않은 상태(sent_at 없음 · 시도 0), 보냄을 기록하면 sent_at · 시도가 바뀐다")
    void savesAndMarksSent() {
        UUID eventId = UUID.randomUUID();
        Long id = save(eventId);
        Map<String, Object> saved = rowOf(id);
        LocalDateTime sentAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        transactionTemplate.executeWithoutResult(status ->
            entityManager.find(OrderOutboxMessage.class, id).markSent(sentAt));

        assertThat(saved).containsEntry("event_id", eventId)
            .containsEntry("event_type", "order.created")
            .containsEntry("topic", "kidly.order.events")
            .containsEntry("message_key", "1024")
            .containsEntry("payload", PAYLOAD)
            .containsEntry("attempt_count", 0)
            .containsEntry("last_error", null)
            .containsEntry("sent_at", null);
        assertThat(saved.get("created_at")).isNotNull().isEqualTo(saved.get("updated_at"));
        Map<String, Object> sent = rowOf(id);
        assertThat(((Timestamp) sent.get("sent_at")).toLocalDateTime()).isEqualTo(sentAt);
        assertThat(sent).containsEntry("attempt_count", 1);
    }

    @Test
    @DisplayName("같은 이벤트 ID는 두 번 저장되지 않는다")
    void eventIdIsUnique() {
        UUID eventId = UUID.randomUUID();
        save(eventId);

        assertThatThrownBy(() -> save(eventId)).rootCause().hasMessageContaining("uk_order_outbox_event_id");
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox", Long.class)).isOne();
    }

    @Test
    @DisplayName("안 보낸 메시지(id) · 보낸 시각(sent_at) 부분 인덱스가 있다")
    void partialIndexes() {
        List<String> indexes = jdbcTemplate.queryForList(
            "select indexdef from pg_indexes where schemaname = 'orders' and tablename = 'order_outbox'", String.class);

        assertThat(indexes)
            .anySatisfy(index -> assertThat(index).contains("(id)").contains("WHERE (sent_at IS NULL)"))
            .anySatisfy(index -> assertThat(index).contains("(sent_at)").contains("WHERE (sent_at IS NOT NULL)"));
    }

    private Long save(UUID eventId) {
        return transactionTemplate.execute(status -> {
            OrderOutboxMessage message = OrderOutboxMessage.create(eventId, "order.created", "kidly.order.events",
                "1024", PAYLOAD);
            entityManager.persist(message);
            return message.getId();
        });
    }

    private Map<String, Object> rowOf(Long id) {
        return jdbcTemplate.queryForMap("select * from orders.order_outbox where id = ?", id);
    }
}
