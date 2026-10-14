package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.Charge;

/**
 * 토스 조회로 확정할 승인 중 충전.
 *
 * @param orderId 토스 주문번호
 * @param amount 충전 금액(원). 토스 승인 금액과 같아야 반영한다
 */
public record ChargeReconcileTarget(Long chargeId, String orderId, long amount) {

    public static ChargeReconcileTarget from(Charge charge) {
        return new ChargeReconcileTarget(charge.getId(), charge.getTossOrderId(), charge.getAmount());
    }
}
