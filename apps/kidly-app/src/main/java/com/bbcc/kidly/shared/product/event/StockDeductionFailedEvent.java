package com.bbcc.kidly.shared.product.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 주문 생성됨을 받았지만 재고를 차감하지 못하고 거절했다. 주문 도메인이 주문 전체를 취소한다.
 * 대상이 주문이라 {@code aggregateId}와 {@code correlationId}가 모두 주문 ID다.
 */
public record StockDeductionFailedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param reason 대표 사유. {@code failures}의 첫 줄 사유다
     * @param failures 거절한 주문상품과 그 사유. 상품이 알 수 있는 만큼만 담는다
     */
    public record Data(
        Long orderId,
        StockDeductionFailureCode reason,
        List<Failure> failures) {
    }

    public record Failure(
        Long orderItemId,
        StockDeductionFailureCode reason) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.orderId());
    }
}
