package com.bbcc.kidly.boundedcontext.order.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 아웃박스 정리(#540)는 DB만 쓰므로 Kafka 없이(커밋 뒤 Kafka 보내기가 꺼진 일반 통합 컨텍스트) 돈다.
 */
@IntegrationTest
class OrderOutboxCleanupIntegrationTest {

    @Autowired
    private OrderOutboxCleanupService cleanupService;

    @Autowired
    private OrderOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("정리는 보관 기간(order.yml 기본 7일)보다 먼저 보낸 메시지만 지우고, 최근에 보낸 것 · 안 보낸 것은 남긴다")
    void cleanUpDeletesSentOlderThanRetention() {
        Long old = save();
        Long recent = save();
        Long unsent = save();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("update orders.order_outbox set sent_at = ? where id = ?", now.minusDays(7).minusMinutes(1),
            old);
        jdbcTemplate.update("update orders.order_outbox set sent_at = ? where id = ?", now.minusDays(7).plusMinutes(1),
            recent);
        jdbcTemplate.update("update orders.order_outbox set created_at = ? where id = ?", now.minusDays(30), unsent);

        int deleted = cleanupService.cleanUp();

        assertThat(deleted).isOne();
        assertThat(jdbcTemplate.queryForList("select id from orders.order_outbox order by id", Long.class))
            .containsExactly(recent, unsent);
    }

    private Long save() {
        return outboxRepository.save(OrderOutboxMessage.create(UUID.randomUUID(), "order.created",
            "kidly.order.events", "1024", "{}")).getId();
    }
}
