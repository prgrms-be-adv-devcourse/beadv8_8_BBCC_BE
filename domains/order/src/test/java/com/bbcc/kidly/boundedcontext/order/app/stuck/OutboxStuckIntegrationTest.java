package com.bbcc.kidly.boundedcontext.order.app.stuck;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.app.stuck.dto.StuckOrderResult;
import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 관리자 멈춘 주문의 아웃박스 멈춤(OUTBOX_STUCK, #602, 실제 PostgreSQL). 릴레이는 id 순으로 보내다 처음 실패한 메시지에서
 * 멈추므로 맨 앞(가장 오래된) 미전송 메시지만 본다: 시도 횟수가 기준 이상이거나 만든 지 기준 시간이 지났으면 1건, 아니면 0건.
 * 보내지면 맨 앞이 바뀌어 저절로 빠진다. 지금(now)을 고정하고 시도 횟수 · 시각은 jdbc로 맞춘다.
 */
@IntegrationTest
class OutboxStuckIntegrationTest {

    private static final String KIND = StuckOrderKind.OUTBOX_STUCK.name();

    @Autowired
    private StuckOrderService stuckOrderService;

    @Autowired
    private StuckOrderThresholds thresholds;

    @Autowired
    private OrderOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now().withNano(0);
    }

    @Test
    @DisplayName("맨 앞 미전송 메시지의 시도 횟수가 기준이면 1건(칸 전부), 기준 − 1이면 0건")
    void stuckByAttempts() {
        OrderOutboxMessage head = unsent("1024", now);
        attempts(head, thresholds.outboxAttempts() - 1, "TimeoutException: 크기 초과");

        assertThat(list()).isEmpty();
        assertThat(stuckOrderService.getSummary(now)).containsEntry(StuckOrderKind.OUTBOX_STUCK, 0L);

        attempts(head, thresholds.outboxAttempts(), "RecordTooLargeException: 크기 초과");
        LocalDateTime lastAttempt = now.minusSeconds(3);
        jdbcTemplate.update("update orders.order_outbox set updated_at = ? where id = ?", lastAttempt, head.getId());

        assertThat(list()).singleElement().satisfies(result -> {
            assertThat(result.kind()).isEqualTo(KIND);
            assertThat(result.orderId()).isEqualTo(1024L);
            assertThat(result.targetType()).isEqualTo("ORDER_OUTBOX");
            assertThat(result.targetId()).isEqualTo(head.getId());
            assertThat(result.status()).isEqualTo("UNSENT");
            assertThat(result.since()).isEqualTo(lastAttempt);
            assertThat(result.outbox()).isEqualTo(new StuckOrderResult.Outbox(head.getId(), "order.created",
                head.getEventId(), thresholds.outboxAttempts(), now, "RecordTooLargeException: 크기 초과", 0));
        });
        assertThat(stuckOrderService.getSummary(now)).containsEntry(StuckOrderKind.OUTBOX_STUCK, 1L);
    }

    @Test
    @DisplayName("맨 앞 미전송 메시지가 만든 지 기준 시간 + 1초 지났으면 1건(since = 만든 시각 + 기준), 정확히 기준이면 0건")
    void stuckByAge() {
        OrderOutboxMessage head = unsent("7", now);
        createdAt(head, now.minus(thresholds.outboxWait()));

        assertThat(list()).isEmpty();

        createdAt(head, now.minus(thresholds.outboxWait()).minusSeconds(1));

        assertThat(list()).singleElement().satisfies(result -> {
            assertThat(result.targetId()).isEqualTo(head.getId());
            assertThat(result.since()).isEqualTo(now.minusSeconds(1));
            assertThat(result.outbox().attemptCount()).isZero();
            assertThat(result.outbox().lastError()).isNull();
        });
    }

    @Test
    @DisplayName("멈춘 메시지가 보내지면 목록 · 요약에서 저절로 빠지고, 다음 맨 앞(방금 만든 것)은 멈춘 것이 아니다")
    void sentDropsFromList() {
        OrderOutboxMessage head = unsent("7", now);
        attempts(head, thresholds.outboxAttempts(), "RecordTooLargeException");
        unsent("8", now);
        assertThat(list()).hasSize(1);

        jdbcTemplate.update("update orders.order_outbox set sent_at = ? where id = ?", now, head.getId());

        assertThat(list()).isEmpty();
        assertThat(stuckOrderService.getSummary(now)).containsEntry(StuckOrderKind.OUTBOX_STUCK, 0L);
    }

    @Test
    @DisplayName("뒤에 밀린 수 = 맨 앞을 뺀 미전송 수(이미 보낸 메시지는 세지 않는다)")
    void backlogCount() {
        OrderOutboxMessage sent = unsent("6", now);
        jdbcTemplate.update("update orders.order_outbox set sent_at = ? where id = ?", now, sent.getId());
        OrderOutboxMessage head = unsent("7", now);
        attempts(head, thresholds.outboxAttempts(), "RecordTooLargeException");
        unsent("7", now);
        unsent("8", now);
        unsent("9", now);

        assertThat(list()).singleElement()
            .satisfies(result -> assertThat(result.outbox().backlogCount()).isEqualTo(3));
    }

    @Test
    @DisplayName("키가 주문 ID(숫자)가 아니면 주문 ID는 null이고 실패하지 않는다")
    void keyNotOrderId() {
        OrderOutboxMessage head = unsent("not-a-number", now);
        attempts(head, thresholds.outboxAttempts(), "RecordTooLargeException");

        assertThat(list()).singleElement().satisfies(result -> assertThat(result.orderId()).isNull());
    }

    @Test
    @DisplayName("멈춘 메시지는 한 건뿐이라 둘째 페이지는 빈 목록, 전체 건수 1")
    void secondPageIsEmpty() {
        attempts(unsent("7", now), thresholds.outboxAttempts(), "RecordTooLargeException");

        Page<StuckOrderResult> second = stuckOrderService.getStuckOrders(KIND, now, 1, 20);

        assertThat(second.getContent()).isEmpty();
        assertThat(second.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("미전송 메시지가 없으면 0건")
    void noUnsent() {
        assertThat(list()).isEmpty();
        assertThat(stuckOrderService.getSummary(now)).containsEntry(StuckOrderKind.OUTBOX_STUCK, 0L);
    }

    private List<StuckOrderResult> list() {
        return stuckOrderService.getStuckOrders(KIND, now, 0, 20).getContent();
    }

    // 지금 만든 미전송 주문 이벤트 메시지(시도 0)
    private OrderOutboxMessage unsent(String key, LocalDateTime createdAt) {
        OrderOutboxMessage message = outboxRepository.save(OrderOutboxMessage.create(UUID.randomUUID(),
            "order.created", "kidly.order.events", key, "{}"));
        createdAt(message, createdAt);
        return message;
    }

    private void createdAt(OrderOutboxMessage message, LocalDateTime createdAt) {
        jdbcTemplate.update("update orders.order_outbox set created_at = ?, updated_at = ? where id = ?",
            createdAt, createdAt, message.getId());
    }

    private void attempts(OrderOutboxMessage message, int attempts, String lastError) {
        jdbcTemplate.update("update orders.order_outbox set attempt_count = ?, last_error = ? where id = ?",
            attempts, lastError, message.getId());
    }
}
