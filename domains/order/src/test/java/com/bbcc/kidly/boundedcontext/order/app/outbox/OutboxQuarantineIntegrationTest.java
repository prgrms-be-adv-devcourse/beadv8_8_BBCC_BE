package com.bbcc.kidly.boundedcontext.order.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.app.config.OrderOutboxProperties;
import com.bbcc.kidly.boundedcontext.order.app.outbox.dto.OutboxRelayResult;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventSender;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository.OutboxMessage;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderKind;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderService;
import com.bbcc.kidly.boundedcontext.order.out.outbox.OrderOutboxMessage;
import com.bbcc.kidly.boundedcontext.order.out.persistence.JpaOrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 PostgreSQL에서 격리 · 키 순서 · 관리자 경쟁 · 커밋 실패를 검증한다.
 * 전송 오류는 포트에서 주입하고, 실제 Kafka 크기 초과는 OrderOutboxRelayIntegrationTest에서 검증한다.
 */
@IntegrationTest
class OutboxQuarantineIntegrationTest {

    private static final String TOPIC = "kidly.order.events";
    private static final String COMMIT_FAILURE = "test_outbox_quarantine_commit_failure";

    @Autowired
    private OrderOutboxRepository repository;

    @Autowired
    private JpaOrderOutboxRepository jpaRepository;

    @Autowired
    private OrderOutboxAdminService adminService;

    @Autowired
    private StuckOrderService stuckService;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanTrigger() {
        jdbc.execute("drop trigger if exists " + COMMIT_FAILURE + " on orders.order_outbox");
        jdbc.execute("drop function if exists " + COMMIT_FAILURE + "()");
    }

    @Test
    @DisplayName("같은 배치와 다음 배치 모두 같은 키를 보류하고 다른 키는 전송한다. 재시도하면 원래 순서로 해제된다")
    void isolatesAndRetriesWithoutOvertaking() {
        Long bad = save("a");
        Long after = save("a");
        Long other = save("b");
        ControlledSender sender = new ControlledSender(bad);
        UUID badEvent = repository.findStatus(bad).orElseThrow().getEventId();
        String payload = jdbc.queryForObject("select payload from orders.order_outbox where id = ?", String.class, bad);

        assertThat(relay(sender).outcome()).isEqualTo(OutboxRelayResult.Outcome.QUARANTINED);
        assertThat(sender.delivered).containsExactly(repository.findStatus(other).orElseThrow().getEventId());
        assertThat(repository.findStatus(bad).orElseThrow().getQuarantinedAt()).isNotNull();
        assertThat(repository.findStatus(after).orElseThrow().getAttemptCount()).isZero();
        assertThat(relay(sender).targets()).isZero();

        sender.reject = false;
        adminService.retry(bad, 99L);
        relay(sender);

        assertThat(sender.delivered).containsExactly(repository.findStatus(other).orElseThrow().getEventId(),
            badEvent, repository.findStatus(after).orElseThrow().getEventId());
        assertThat(repository.findStatus(bad).orElseThrow().getEventId()).isEqualTo(badEvent);
        assertThat(jdbc.queryForObject("select payload from orders.order_outbox where id = ?", String.class, bad))
            .isEqualTo(payload);
        assertThat(repository.findStatus(bad).orElseThrow().getAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("배치 상한보다 많은 같은 키가 밀려도 다른 키는 SQL 조회에서 먼저 찾을 수 있다")
    void blockedStreamCannotStarveAnotherKey() {
        Long blocked = save("a");
        transactions.executeWithoutResult(s -> repository.quarantine(blocked, "RecordTooLargeException",
            LocalDateTime.now()));
        for (int i = 0; i < 150; i++) {
            save("a");
        }
        Long other = save("b");
        List<OutboxMessage> candidates = transactions.execute(s -> repository.findUnsent(Limit.of(1)));
        assertThat(candidates).extracting(OutboxMessage::id).containsExactly(other);
    }

    @Test
    @DisplayName("관리자 재시도가 겹쳐도 원본과 시도 횟수는 유지하고 처리자 · 재시도 횟수는 한 번만 기록한다")
    void concurrentAdminRetryIsIdempotent() throws Exception {
        Long id = save("a");
        transactions.executeWithoutResult(s -> repository.quarantine(id, "RecordTooLargeException",
            LocalDateTime.now()));
        ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
            () -> adminService.retry(id, 99L), () -> adminService.retry(id, 99L)));
        assertThat(outcome.unexpected()).isEmpty();
        var row = repository.findStatus(id).orElseThrow();
        assertThat(row.getQuarantinedAt()).isNull();
        assertThat(row.getRetryCount()).isOne();
        assertThat(row.getLastRetriedBy()).isEqualTo(99L);
        assertThat(row.getLastRetriedAt()).isNotNull();
        assertThat(row.getAttemptCount()).isOne();
        assertThat(row.getLastError()).isEqualTo("RecordTooLargeException");
    }

    @Test
    @DisplayName("격리는 기준 시간 · 횟수를 기다리지 않고 멈춘 주문에 표시하고 backlog는 같은 키의 후속 건만 센다")
    void quarantineAppearsImmediatelyInStuckSummary() {
        save("healthy-head");
        Long blocked = save("1024");
        save("1024");
        save("independent");
        transactions.executeWithoutResult(s -> repository.quarantine(blocked, "RecordTooLargeException",
            LocalDateTime.now()));
        assertThat(stuckService.getSummary(LocalDateTime.now())).containsEntry(StuckOrderKind.OUTBOX_STUCK, 1L);
        assertThat(stuckService.getStuckOrders("OUTBOX_STUCK", LocalDateTime.now(), 0, 20)).singleElement()
            .satisfies(row -> {
                assertThat(row.targetId()).isEqualTo(blocked);
                assertThat(row.outbox().quarantinedAt()).isNotNull();
                assertThat(row.outbox().backlogCount()).isOne();
                assertThat(row.orderId()).isEqualTo(1024L);
            });
    }

    @Test
    @DisplayName("격리가 있어도 다른 키의 기준을 넘긴 실제 전송 대기는 대표에서 가려지지 않는다")
    void globalFailureRemainsVisibleWithQuarantine() {
        Long isolated = save("isolated");
        save("isolated");
        Long failing = save("global-failure");
        transactions.executeWithoutResult(s -> repository.quarantine(isolated, "RecordTooLargeException",
            LocalDateTime.now()));
        jdbc.update("update orders.order_outbox set attempt_count = 100, last_error = 'TimeoutException' where id = ?",
            failing);
        assertThat(stuckService.getStuckOrders("OUTBOX_STUCK", LocalDateTime.now(), 0, 20)).singleElement()
            .satisfies(row -> {
                assertThat(row.targetId()).isEqualTo(failing);
                assertThat(row.outbox().quarantinedAt()).isNull();
            });
        assertThat(repository.countQuarantined()).isOne();
    }

    @Test
    @DisplayName("브로커 환경 오류가 반복돼도 정상 메시지를 격리하지 않고 전송 여부가 불명확한 메시지에서 멈춘다")
    void repeatedTransientFailureDoesNotQuarantine() {
        Long id = save("a");
        Long other = save("b");
        OrderEventSender sender = message -> {
            throw new IllegalStateException("broker unavailable");
        };
        for (int i = 0; i < 12; i++) {
            assertThat(relay(sender).outcome()).isEqualTo(OutboxRelayResult.Outcome.STOPPED);
        }
        assertThat(repository.findStatus(id).orElseThrow().getQuarantinedAt()).isNull();
        assertThat(repository.findStatus(id).orElseThrow().getAttemptCount()).isEqualTo(12);
        assertThat(repository.findStatus(other).orElseThrow().getAttemptCount()).isZero();
        assertThat(repository.countQuarantined()).isZero();
    }

    @Test
    @DisplayName("전송 뒤 DB 커밋이 실패하면 격리 · 보냄 기록을 함께 롤백하고 같은 이벤트 ID로 다시 전송한다")
    void commitFailureReplaysSameEvent() {
        Long bad = save("a");
        Long good = save("b");
        UUID goodEvent = repository.findStatus(good).orElseThrow().getEventId();
        ControlledSender sender = new ControlledSender(bad);
        jdbc.execute("create function " + COMMIT_FAILURE + "() returns trigger language plpgsql as $$ "
            + "begin raise exception 'forced outbox commit failure'; end; $$");
        jdbc.execute("create constraint trigger " + COMMIT_FAILURE + " after update on orders.order_outbox "
            + "deferrable initially deferred for each row execute function " + COMMIT_FAILURE + "()");

        assertThatThrownBy(() -> relay(sender)).isInstanceOf(RuntimeException.class);
        assertThat(sender.delivered).containsExactly(goodEvent);
        assertThat(repository.findStatus(bad).orElseThrow().getQuarantinedAt()).isNull();
        assertThat(repository.findStatus(good).orElseThrow().getSentAt()).isNull();
        cleanTrigger();
        relay(sender);
        assertThat(sender.delivered).containsExactly(goodEvent, goodEvent);
        assertThat(repository.findStatus(bad).orElseThrow().getQuarantinedAt()).isNotNull();
    }

    private OutboxRelayResult relay(OrderEventSender sender) {
        var properties = new OrderOutboxProperties(Duration.ofSeconds(1), 100, Duration.ofDays(7));
        return transactions.execute(s -> new OrderOutboxRelayService(repository, sender, properties).relay());
    }

    private Long save(String key) {
        return jpaRepository.save(OrderOutboxMessage.create(UUID.randomUUID(), "order.created", TOPIC, key, "{}"))
            .getId();
    }

    private static final class ControlledSender implements OrderEventSender {
        private final Long failingId;
        private final List<UUID> delivered = new ArrayList<>();
        private boolean reject = true;

        private ControlledSender(Long failingId) {
            this.failingId = failingId;
        }

        @Override
        public void send(OutboxMessage message) {
            if (reject && message.id().equals(failingId)) {
                throw new RecordTooLargeException("size");
            }
            delivered.add(message.eventId());
        }

        @Override
        public boolean isPermanentFailure(RuntimeException error) {
            return error instanceof RecordTooLargeException;
        }
    }
}
