package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.DevChargeResult;

/**
 * @param balance 충전 뒤 잔액(원)
 */
public record DevChargeResponse(Long devChargeId, Long ledgerId, long amount, long balance) {

    public static DevChargeResponse from(DevChargeResult result) {
        return new DevChargeResponse(result.devChargeId(), result.ledgerId(), result.amount(), result.balance());
    }
}
