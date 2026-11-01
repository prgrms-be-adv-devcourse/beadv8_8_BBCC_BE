package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 판매자가 발송 전 자기 몫(배송 한 건)의 주문상품을 취소했다(#655). 결제 도메인이 주문상품별로 부분 환불하고, 상품 도메인이 재고를 되돌린다.
 * 마지막 판매자 몫이 취소돼 주문이 취소돼도 이 이벤트만 나간다(order.cancelled는 나가지 않는다: 환불 · 재고 복구 중복 방지).
 * 대상이 취소 한 건이라 {@code aggregateId}는 취소 ID, 같은 주문의 흐름을 묶도록 {@code correlationId}는 주문 ID다.
 */
public record OrderItemCancelledEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param sellerId 취소한 몫의 판매자(회원 ID)
     * @param cancellationId 이 취소의 ID. 결제가 환불(payment.refunded)에 실어 어느 취소의 환불인지 맞춘다
     * @param reason 취소 사유(OUT_OF_STOCK · WRONG_PRODUCT_INFO · OTHER)
     * @param items 이번에 취소한 주문상품
     */
    public record Data(
        Long orderId,
        Long memberId,
        Long sellerId,
        Long cancellationId,
        String reason,
        List<Item> items) {
    }

    /**
     * @param amount 주문상품 금액(원). 환불할 금액이다
     */
    public record Item(
        Long orderItemId,
        Long optionId,
        int quantity,
        long amount) {
    }

    /**
     * 대상이 주문상품이라 클래스 이름 규칙(order.item-cancelled) 대신 계약 이름을 쓴다.
     */
    @Override
    public String eventType() {
        return "order-item.cancelled";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.cancellationId());
    }

    @Override
    public String correlationId() {
        return String.valueOf(data.orderId());
    }
}
