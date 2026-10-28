package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeConfirmResult;

/**
 * @param status DONE이면 입금 완료, CONFIRMING이면 토스 결과를 아직 모른다
 * @param balance 입금 후 잔액(원). CONFIRMING이면 null
 */
public record ChargeConfirmResponse(Long chargeId, String status, long amount, Long balance) {

    public static ChargeConfirmResponse from(ChargeConfirmResult result) {
        return new ChargeConfirmResponse(result.chargeId(), result.status(), result.amount(), result.balance());
    }
}
