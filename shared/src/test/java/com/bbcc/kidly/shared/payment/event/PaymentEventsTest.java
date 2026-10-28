package com.bbcc.kidly.shared.payment.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.domain.PayoutFailureCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentEventsTest {

    private static final UUID EVENT_ID = UUID.fromString("0b9a7c1e-2f44-4c1d-9a51-7e3d2c8f6b10");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 30);

    @Test
    @DisplayName("결제 완료 이벤트는 payment.completed이고 대상은 결제, 흐름은 주문이다")
    void paymentCompleted() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(EVENT_ID, NOW, new PaymentCompletedEvent.Data(
            620L, 900L, 12L, 87_000L,
            List.of(new PaymentCompletedEvent.Item(5001L, 58_000L), new PaymentCompletedEvent.Item(5002L, 29_000L)),
            NOW));

        assertContract(event, "payment.completed", "620", "900");
    }

    @Test
    @DisplayName("결제 실패 이벤트는 payment.failed이고 대상은 결제, 흐름은 주문이다")
    void paymentFailed() {
        PaymentFailedEvent event = new PaymentFailedEvent(EVENT_ID, NOW, new PaymentFailedEvent.Data(
            621L, 901L, 12L, 87_000L, PaymentFailureCode.INSUFFICIENT_BALANCE, NOW));

        assertContract(event, "payment.failed", "621", "901");
    }

    @Test
    @DisplayName("결제를 저장하지 않은 실패 이벤트는 대상도 주문이다")
    void paymentFailedWithoutPayment() {
        PaymentFailedEvent event = new PaymentFailedEvent(EVENT_ID, NOW, new PaymentFailedEvent.Data(
            null, 901L, 12L, 87_000L, PaymentFailureCode.WALLET_NOT_FOUND, NOW));

        assertContract(event, "payment.failed", "901", "901");
    }

    @Test
    @DisplayName("환불 완료 이벤트는 payment.refunded이고 대상은 환불, 흐름은 주문이다")
    void paymentRefunded() {
        PaymentRefundedEvent event = new PaymentRefundedEvent(EVENT_ID, NOW, new PaymentRefundedEvent.Data(
            81L, 620L, 900L, 7001L, List.of(new PaymentRefundedEvent.Item(5001L, 58_000L)), 58_000L, NOW));

        assertContract(event, "payment.refunded", "81", "900");
    }

    @Test
    @DisplayName("정산 지급 완료 이벤트는 payment.payout-completed이고 대상 · 흐름은 정산서다")
    void paymentPayoutCompleted() {
        PaymentPayoutCompletedEvent event = new PaymentPayoutCompletedEvent(EVENT_ID, NOW,
            new PaymentPayoutCompletedEvent.Data(31L, 7L, 270_000L, 4001L, NOW));

        assertContract(event, "payment.payout-completed", "31", "31");
    }

    @Test
    @DisplayName("정산 지급 실패 이벤트는 payment.payout-failed이고 대상 · 흐름은 정산서다")
    void paymentPayoutFailed() {
        PaymentPayoutFailedEvent event = new PaymentPayoutFailedEvent(EVENT_ID, NOW,
            new PaymentPayoutFailedEvent.Data(31L, 7L, 270_000L, PayoutFailureCode.WALLET_NOT_FOUND, NOW));

        assertContract(event, "payment.payout-failed", "31", "31");
    }

    private static void assertContract(DomainEvent event, String eventType, String aggregateId,
        String correlationId) {
        assertThat(event.eventType()).isEqualTo(eventType);
        assertThat(event.producer()).isEqualTo("payment");
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
        assertThat(event.correlationId()).isEqualTo(correlationId);
    }
}
