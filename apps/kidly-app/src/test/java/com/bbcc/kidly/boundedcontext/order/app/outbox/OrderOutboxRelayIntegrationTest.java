package com.bbcc.kidly.boundedcontext.order.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.app.outbox.dto.OutboxRelayResult;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 아웃박스 릴레이(#540)를 실제 PostgreSQL · 테스트용 Kafka로 확인한다. 보내기가 실패하는 경우를 만들려고
 * 선언하지 않은 토픽(자동 생성 꺼짐)으로 메시지를 둔다: 메타데이터 대기(max.block 5초) 뒤 실패한다.
 * 스케줄러는 테스트 설정으로 멈춰 두고 릴레이를 직접 부른다.
 */
@IntegrationTest
class OrderOutboxRelayIntegrationTest {

    private static final Duration ONE_RUN_LIMIT = Duration.ofSeconds(8);

    @Autowired
    private OrderOutboxRelayService relayService;

    @Autowired
    private OrderOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Kafka가 받지 못하는 메시지면 한 주기가 메타데이터 대기 5초 언저리에서 멈추고, 실패를 기록한 채 보내지 않은 상태로 남는다")
    void oneRunIsBoundedWhenKafkaCannotAccept() {
        Long id = saveToUndeclaredTopic();

        long started = System.nanoTime();
        OutboxRelayResult result = relayService.relay();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(result).isEqualTo(new OutboxRelayResult(OutboxRelayResult.Outcome.STOPPED, 1, 0));
        assertThat(elapsed).isLessThan(ONE_RUN_LIMIT);
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select sent_at, attempt_count, last_error from orders.order_outbox where id = ?", id);
        assertThat(row).containsEntry("sent_at", null).containsEntry("attempt_count", 1);
        assertThat((String) row.get("last_error")).contains("not present in metadata after 5000 ms");
    }

    @Test
    @DisplayName("두 릴레이가 동시에 돌면 잠금을 얻은 하나만 보내고 다른 하나는 아무것도 하지 않는다")
    void onlyOneRelayRunsAtATime() throws InterruptedException {
        Long id = saveToUndeclaredTopic();
        List<OutboxRelayResult> results = new CopyOnWriteArrayList<>();
        Runnable relay = () -> results.add(relayService.relay());

        ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(relay, relay));

        assertThat(outcome.unexpected()).isEmpty();
        assertThat(results).extracting(OutboxRelayResult::outcome)
            .containsExactlyInAnyOrder(OutboxRelayResult.Outcome.STOPPED, OutboxRelayResult.Outcome.LOCKED);
        assertThat(jdbcTemplate.queryForObject("select attempt_count from orders.order_outbox where id = ?",
            Integer.class, id)).as("보내기를 시도한 릴레이는 하나").isOne();
    }

    @Test
    @DisplayName("정리는 보관 기간(order.yml 기본 7일)보다 먼저 보낸 메시지만 지우고, 최근에 보낸 것 · 안 보낸 것은 남긴다")
    void cleanUpDeletesSentOlderThanRetention() {
        Long old = saveToUndeclaredTopic();
        Long recent = saveToUndeclaredTopic();
        Long unsent = saveToUndeclaredTopic();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("update orders.order_outbox set sent_at = ? where id = ?", now.minusDays(7).minusMinutes(1),
            old);
        jdbcTemplate.update("update orders.order_outbox set sent_at = ? where id = ?", now.minusDays(7).plusMinutes(1),
            recent);
        jdbcTemplate.update("update orders.order_outbox set created_at = ? where id = ?", now.minusDays(30), unsent);

        int deleted = relayService.cleanUp();

        assertThat(deleted).isOne();
        assertThat(jdbcTemplate.queryForList("select id from orders.order_outbox order by id", Long.class))
            .containsExactly(recent, unsent);
    }

    private Long saveToUndeclaredTopic() {
        return outboxRepository.save(OrderOutboxMessage.create(UUID.randomUUID(), "order.created",
            "undeclared-" + UUID.randomUUID(), "1024", "{}")).getId();
    }
}
