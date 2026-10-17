package com.bbcc.kidly.boundedcontext.payment.app;

/**
 * 결제 행동 로그의 action. 예치금 · 충전 · 결제 상태가 바뀐 일이다. 조회는 남기지 않는다.
 */
public enum PaymentAction {
    CHARGE_REQUESTED,
    CHARGE_CONFIRM_STARTED,
    CHARGE_CONFIRMED,
    CHARGE_FAILED,
    CHARGE_EXPIRED,
    CHARGE_CANCEL_REQUESTED,
    CHARGE_CANCELLED,
    CHARGE_CANCEL_FAILED,
    CHARGE_CANCELLED_EXTERNALLY,
    ORDER_PAID,
    ORDER_PAYMENT_FAILED,
    ORDER_CANCEL_RECORDED,
    ORDER_REFUNDED,
    PAYOUT_COMPLETED
}
