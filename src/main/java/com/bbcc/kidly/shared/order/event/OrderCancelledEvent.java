package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 주문이 취소됐다. 결제대기 주문(재고 부족 · 결제 실패 · 결제 마감 만료 · 구매자 취소), 또는 발송 전 결제완료 주문의
 * 구매자 취소(#354). 상품 도메인은 차감했던 재고를 되돌리고, 결제 도메인은 결제완료였으면 환불한다(사유와 상관없이).
 * 대상이 주문이라 {@code aggregateId}와 {@code correlationId}가 모두 주문 ID다.
 */
public record OrderCancelledEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param reason 취소 사유 이름 (PAYMENT_FAILED · PAYMENT_EXPIRED · OUT_OF_STOCK · BUYER_CANCELLED)
     * @param items 주문상품 전체, 주문한 순서(주문상품 ID 오름차순)
     */
    public record Data(
        Long orderId,
        Long memberId,
        String reason,
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
