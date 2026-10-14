package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeRequestResult;
import java.time.LocalDateTime;

/**
 * @param orderId 토스 결제창에 넘길 주문번호
 * @param expiresAt 이 시각이 지나면 승인할 수 없다
 */
public record ChargeRequestResponse(Long chargeId, String orderId, long amount, LocalDateTime expiresAt) {

    public static ChargeRequestResponse from(ChargeRequestResult result) {
        return new ChargeRequestResponse(result.chargeId(), result.orderId(), result.amount(), result.expiresAt());
    }
}
