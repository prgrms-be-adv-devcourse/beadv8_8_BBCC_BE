package com.bbcc.kidly.boundedcontext.order.app.stuck;

import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;

/**
 * 멈춘 주문의 종류(#445). 종류마다 조건이 대상의 상태를 하나로 정하므로 응답의 대상 종류 · 상태를 여기서 정한다.
 * 선언 순서가 건수 요약(summary)의 키 순서다.
 */
public enum StuckOrderKind {
    STOCK_CHECK_STALE(OrderStatusHistory.TargetType.ORDER, Order.Status.PENDING_PAYMENT),
    PAYMENT_EXPIRY_MISSED(OrderStatusHistory.TargetType.ORDER, Order.Status.PENDING_PAYMENT),
    PAID_WITHOUT_DELIVERY(OrderStatusHistory.TargetType.ORDER, Order.Status.PAID),
    RECOVERY_PENDING_STALE(OrderStatusHistory.TargetType.PAYMENT_RECOVERY, PaymentRecovery.Status.PENDING),
    SHIPPING_DELAYED(OrderStatusHistory.TargetType.DELIVERY, Delivery.Status.READY),
    AUTO_CONFIRM_MISSED(OrderStatusHistory.TargetType.ORDER_ITEM, OrderItem.Status.ORDERED);

    private final OrderStatusHistory.TargetType targetType;
    private final Enum<?> status;

    StuckOrderKind(OrderStatusHistory.TargetType targetType, Enum<?> status) {
        this.targetType = targetType;
        this.status = status;
    }

    public String targetType() {
        return targetType.name();
    }

    public String status() {
        return status.name();
    }
}
