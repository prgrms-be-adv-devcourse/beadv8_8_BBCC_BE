package com.bbcc.kidly.shared.product.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 생성됨을 받아 그 주문의 모든 주문상품 재고를 차감했다. 주문 도메인이 결제를 요청한다(결제대기일 때만).
 * 대상이 주문이라 {@code aggregateId}와 {@code correlationId}가 모두 주문 ID다.
 */
public record StockDeductedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(
        Long orderId) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.orderId());
    }
}
