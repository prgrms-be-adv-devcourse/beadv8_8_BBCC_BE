package com.bbcc.kidly.shared.payment.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 주문 금액만큼 예치금을 차감해 결제가 완료됐다. 주문 도메인이 재고를 확정하고 구매 완료로 바꾼다.
 * 주문당 결제는 한 번이다. 같은 흐름의 이벤트를 묶도록 {@code correlationId}는 주문 ID다.
 */
public record PaymentCompletedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param amount 결제 금액(원). {@code items}의 금액 합과 같다
     * @param items 주문상품별 결제 금액
     */
    public record Data(
        Long paymentId,
        Long orderId,
        Long memberId,
        long amount,
        List<Item> items,
        LocalDateTime paidAt) {
    }

    /**
     * @param amount 주문상품 하나의 결제 금액(원)
     */
    public record Item(Long orderItemId, long amount) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.paymentId());
    }

    @Override
    public String correlationId() {
        return String.valueOf(data.orderId());
    }
}
