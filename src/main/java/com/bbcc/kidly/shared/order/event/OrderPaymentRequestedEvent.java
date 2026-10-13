package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 재고 차감이 끝나 주문의 결제를 요청한다. 결제 도메인이 주문상품 단위로 결제 · 환불한다.
 * 대상이 주문이라 {@code aggregateId}와 {@code correlationId}가 모두 주문 ID다.
 */
public record OrderPaymentRequestedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param amount 주문 총액(원). {@code items}의 금액 합과 같다
     */
    public record Data(
        Long orderId,
        Long memberId,
        long amount,
        List<Item> items) {
    }

    /**
     * @param amount 주문상품 하나의 금액(원)
     */
    public record Item(Long orderItemId, long amount) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.orderId());
    }
}
