package com.bbcc.kidly.shared.payment.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 주문상품 취소에 따른 예치금 환불이 완료됐다. 주문 도메인이 취소를 끝내고, 정산 도메인이 미정산 대상을 제외한다.
 * 환불 사유는 주문의 취소 기록에 있으므로 담지 않는다.
 */
public record PaymentRefundedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param cancellationId 이 환불을 만든 주문상품 취소 ID. 주문이 어느 취소의 환불인지 맞춰 본다
     * @param items 주문상품별 환불 금액
     * @param amount 총 환불 금액(원). {@code items}의 금액 합과 같다
     */
    public record Data(
        Long refundId,
        Long paymentId,
        Long orderId,
        Long cancellationId,
        List<Item> items,
        long amount,
        LocalDateTime refundedAt) {
    }

    /**
     * @param amount 주문상품 하나의 환불 금액(원)
     */
    public record Item(Long orderItemId, long amount) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.refundId());
    }

    @Override
    public String correlationId() {
        return String.valueOf(data.orderId());
    }
}
