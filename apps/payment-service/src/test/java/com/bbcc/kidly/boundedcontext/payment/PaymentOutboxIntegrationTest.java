package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.payment.domain.PayoutFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 이벤트를 업무와 같은 트랜잭션에서 아웃박스에 저장하는지, 정리가 보관 기간이 지난 보낸 메시지만 지우는지 본다(#550).
 * Kafka 전송은 PaymentKafkaPublishIntegrationTest가 본다.
 */
@IntegrationTest
@RecordApplicationEvents
class PaymentOutboxIntegrationTest {

    private static final Long SETTLEMENT_ID = 31L;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PaymentOutboxRepository outboxRepository;

    @Autowired
    private PaymentOutboxCleanupService cleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("결제가 완료되면 payment.completed를 주문 ID 키로 아웃박스에 저장한다. 주문이 발행한 결제 요청은 저장하지 않는다")
    void recordPaymentCompleted() {
        walletRepository.save(PaymentFixture.wallet(20_000L));
        OrderPaymentRequestedEvent requested = new OrderPaymentRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderPaymentRequestedEvent.Data(ORDER_ID, MEMBER_ID, 15_000L,
                List.of(new OrderPaymentRequestedEvent.Item(1000L, 15_000L))));

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(requested));

        // 결제는 주문 트랜잭션이 커밋된 뒤 리스너의 새 트랜잭션에서 처리되고, 그 트랜잭션의 커밋 직전에 저장된다
        PaymentCompletedEvent completed = events.stream(PaymentCompletedEvent.class).findFirst().orElseThrow();
        Map<String, Object> row = jdbcTemplate.queryForMap("select event_type, topic, message_key, sent_at, "
            + "attempt_count from payment.payment_outbox where event_id = ?", completed.eventId());
        assertThat(row).containsEntry("event_type", "payment.completed")
            .containsEntry("topic", "kidly.payment.events")
            .containsEntry("message_key", String.valueOf(ORDER_ID))
            .containsEntry("sent_at", null)
            .containsEntry("attempt_count", 0);
        assertThat(countByEventId(requested.eventId())).isZero();
    }

    @Test
    @DisplayName("업무가 롤백되면 메시지도 남지 않는다")
    void rolledBack() {
        PaymentPayoutFailedEvent event = payoutFailed();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(event);
            status.setRollbackOnly();
        });

        assertThat(countByEventId(event.eventId())).isZero();
    }

    @Test
    @DisplayName("같은 이벤트를 다시 발행해도 메시지는 하나다. 업무는 실패하지 않는다")
    void sameEventOnce() {
        PaymentPayoutFailedEvent event = payoutFailed();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertThat(countByEventId(event.eventId())).isOne();
    }

    @Test
    @DisplayName("정리는 보관 기간(7일)이 지난 보낸 메시지만 지운다. 안 보낸 메시지는 오래돼도 남긴다")
    void cleanUp() {
        Long oldSent = saveSentAt(LocalDateTime.now().minusDays(8));
        Long recentSent = saveSentAt(LocalDateTime.now().minusDays(6));
        Long unsent = outboxRepository.save(PaymentFixture.outboxMessage()).getId();
        jdbcTemplate.update("update payment.payment_outbox set created_at = ? where id = ?",
            LocalDateTime.now().minusDays(30), unsent);

        int deleted = cleanupService.cleanUp();

        assertThat(deleted).isOne();
        assertThat(jdbcTemplate.queryForList("select id from payment.payment_outbox", Long.class))
            .containsExactlyInAnyOrder(recentSent, unsent)
            .doesNotContain(oldSent);
    }

    private Long saveSentAt(LocalDateTime sentAt) {
        Long id = outboxRepository.save(PaymentFixture.outboxMessage()).getId();
        jdbcTemplate.update("update payment.payment_outbox set sent_at = ?, attempt_count = 1 where id = ?",
            sentAt, id);
        return id;
    }

    private int countByEventId(UUID eventId) {
        return jdbcTemplate.queryForObject("select count(*) from payment.payment_outbox where event_id = ?",
            Integer.class, eventId);
    }

    private static PaymentPayoutFailedEvent payoutFailed() {
        return new PaymentPayoutFailedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentPayoutFailedEvent.Data(SETTLEMENT_ID, MEMBER_ID, 270_000L, PayoutFailureCode.WALLET_NOT_FOUND,
                LocalDateTime.now()));
    }
}
