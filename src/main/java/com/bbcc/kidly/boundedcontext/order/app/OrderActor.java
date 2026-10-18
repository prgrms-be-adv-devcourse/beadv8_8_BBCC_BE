package com.bbcc.kidly.boundedcontext.order.app;

import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import java.time.LocalDateTime;

/**
 * 상태를 바꾼 주체와 그 요청 · 작업 · 이벤트 이름(#442). 주문 · 배송 · 결제 복구 기능이 함께 쓰므로 app 바로 아래에 둔다.
 * source는 관리자가 이력에서 바로 알아볼 수 있게 HTTP 경로 틀(path 변수는 이름 그대로) · 스케줄러 작업 이름 · 받은 이벤트 타입이다.
 *
 * @param id 회원 ID(구매자 · 판매자 · 관리자). 시스템 · 이벤트는 null
 */
public record OrderActor(OrderStatusHistory.ActorType type, Long id, String source) {

    public static final String PLACE = "POST /api/v1/orders";
    public static final String BUYER_CANCEL = "POST /api/v1/orders/{orderId}/cancel";
    public static final String BUYER_CONFIRM = "POST /api/v1/orders/{orderId}/items/{orderItemId}/confirm";
    public static final String SELLER_SHIP = "POST /api/v1/seller/orders/{sellerOrderId}/ship";
    public static final String SELLER_DELIVER = "POST /api/v1/seller/orders/{sellerOrderId}/deliver";
    public static final String RECOVERY_RETRY = "POST /api/v1/admin/orders/recoveries/{id}/retry";
    public static final String RECOVERY_CANCEL = "POST /api/v1/admin/orders/recoveries/{id}/cancel";
    public static final String PAYMENT_EXPIRY = "scheduler:payment-expiry";
    public static final String AUTO_CONFIRM = "scheduler:auto-confirm";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String STOCK_DEDUCTION_FAILED = "stock.deduction-failed";

    public static OrderActor buyer(Long memberId, String source) {
        return new OrderActor(OrderStatusHistory.ActorType.BUYER, memberId, source);
    }

    public static OrderActor seller(Long memberId, String source) {
        return new OrderActor(OrderStatusHistory.ActorType.SELLER, memberId, source);
    }

    public static OrderActor admin(Long memberId, String source) {
        return new OrderActor(OrderStatusHistory.ActorType.ADMIN, memberId, source);
    }

    public static OrderActor system(String job) {
        return new OrderActor(OrderStatusHistory.ActorType.SYSTEM, null, job);
    }

    public static OrderActor event(String eventType) {
        return new OrderActor(OrderStatusHistory.ActorType.EVENT, null, eventType);
    }

    /**
     * 이 주체가 바꾼 상태 한 줄. 상태는 대상마다 enum이 달라 이름으로 남긴다.
     *
     * @param from 이전 상태. 새로 만들었으면 null
     */
    public OrderStatusHistory history(Long orderId, OrderStatusHistory.TargetType targetType, Long targetId,
        Enum<?> from, Enum<?> to, String reason, LocalDateTime occurredAt) {
        return OrderStatusHistory.of(orderId, targetType, targetId, from == null ? null : from.name(), to.name(),
            reason, type, id, source, occurredAt);
    }
}
