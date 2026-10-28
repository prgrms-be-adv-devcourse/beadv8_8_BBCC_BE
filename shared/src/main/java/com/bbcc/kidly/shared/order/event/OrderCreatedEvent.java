package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 주문을 만들었다(결제대기). 상품 도메인이 주문상품마다 재고를 차감한다.
 * 대상이 주문이라 {@code aggregateId}와 {@code correlationId}가 모두 주문 ID다.
 */
public record OrderCreatedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(
        Long orderId,
        Long memberId,
        List<Item> items) {
    }

    public record Item(
        Long orderItemId,
        Long optionId,
        int quantity) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.orderId());
    }
}
