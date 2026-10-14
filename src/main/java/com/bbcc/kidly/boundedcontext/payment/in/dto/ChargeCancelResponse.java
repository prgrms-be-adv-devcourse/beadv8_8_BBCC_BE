package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeCancelResult;

/**
 * @param status CANCELLED면 취소 완료, CANCELLING이면 토스 결과를 아직 모른다
 * @param balance 취소 후 잔액(원). CANCELLING이면 null
 */
public record ChargeCancelResponse(Long chargeId, String status, long amount, Long balance) {

    public static ChargeCancelResponse from(ChargeCancelResult result) {
        return new ChargeCancelResponse(result.chargeId(), result.status(), result.amount(), result.balance());
    }
}
