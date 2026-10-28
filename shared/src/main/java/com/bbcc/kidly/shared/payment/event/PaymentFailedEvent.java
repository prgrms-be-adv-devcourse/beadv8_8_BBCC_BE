package com.bbcc.kidly.shared.payment.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 예치금 결제가 확정적으로 실패했다. 주문 도메인이 구매 실패로 바꾸고 선점 재고를 해제한다.
 * 결과를 아직 모르는 타임아웃에는 발행하지 않는다. 실패한 주문은 다시 결제하지 않고 새 주문으로 결제한다.
 * 잔액 부족만 실패 결제를 저장한다. 지갑이 없거나 금액이 맞지 않으면 저장하지 않으므로 {@code paymentId}가 null이고,
 * 이때는 {@code aggregateId}도 주문 ID다.
 */
public record PaymentFailedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param paymentId 실패로 저장한 결제 ID. 저장하지 않은 실패(WALLET_NOT_FOUND · AMOUNT_MISMATCH)면 null
     * @param requestedAmount 결제하려던 금액(원)
     */
    public record Data(
        Long paymentId,
        Long orderId,
        Long memberId,
        long requestedAmount,
        PaymentFailureCode reasonCode,
        LocalDateTime failedAt) {
    }

    @Override
    public String aggregateId() {
        return data.paymentId() == null ? correlationId() : String.valueOf(data.paymentId());
    }

    @Override
    public String correlationId() {
        return String.valueOf(data.orderId());
    }
}
