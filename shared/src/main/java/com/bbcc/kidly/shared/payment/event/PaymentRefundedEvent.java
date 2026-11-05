package com.bbcc.kidly.shared.payment.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 주문 취소에 따른 예치금 환불이 완료됐다. 주문상품 취소(order-item.cancelled)와 주문 전체 취소(order.cancelled) 모두 보낸다.
 * 알림 도메인이 환불받은 회원에게 환불 완료를 알린다. 실패한 환불은 보내지 않는다.
 * 환불 사유는 주문의 취소 기록에 있으므로 담지 않는다.
 */
public record PaymentRefundedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param cancellationId 이 환불을 만든 주문상품 취소 ID. 주문 전체 취소로 환불하면 null
     * @param items 주문상품별 환불 금액
     * @param amount 총 환불 금액(원). {@code items}의 금액 합과 같다
     * @param memberId 환불받은 회원 ID. 이 필드를 더하기 전의 메시지에는 없다(null)
     */
    public record Data(
        Long refundId,
        Long paymentId,
        Long orderId,
        Long cancellationId,
        List<Item> items,
        long amount,
        LocalDateTime refundedAt,
        Long memberId) {
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
