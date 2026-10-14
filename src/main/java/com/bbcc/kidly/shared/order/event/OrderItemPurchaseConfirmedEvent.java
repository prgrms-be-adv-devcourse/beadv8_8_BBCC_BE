package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문상품을 구매확정했다(직접 · 자동). 정산 · 추천 도메인이 구매 기록으로 쓴다.
 * 대상이 주문상품이라 {@code aggregateId}는 주문상품 ID, 같은 주문의 흐름을 묶도록 {@code correlationId}는 주문 ID다.
 */
public record OrderItemPurchaseConfirmedEvent(UUID eventId, LocalDateTime occurredAt, Data data)
    implements DomainEvent {

    /**
     * @param childId 자녀를 고르지 않았으면 null
     * @param amount 주문상품 금액(원)
     */
    public record Data(
        Long orderItemId,
        Long orderId,
        Long memberId,
        Long sellerId,
        Long childId,
        Long productId,
        Long optionId,
        String size,
        int quantity,
        long amount,
        LocalDateTime confirmedAt) {
    }

    /**
     * 대상이 주문상품이라 클래스 이름 규칙(order.item-purchase-confirmed) 대신 계약 이름을 쓴다.
     */
    @Override
    public String eventType() {
        return "order-item.purchase-confirmed";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.orderItemId());
    }

    @Override
    public String correlationId() {
        return String.valueOf(data.orderId());
    }
}
