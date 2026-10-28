package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import java.time.LocalDateTime;

/**
 * 충전 요청 결과. 프런트는 {@code orderId} · {@code amount}로 토스 결제창을 연다.
 *
 * @param orderId 토스 주문번호
 * @param expiresAt 이 시각이 지나면 승인할 수 없다
 */
public record ChargeRequestResult(Long chargeId, String orderId, long amount, LocalDateTime expiresAt) {

    public static ChargeRequestResult from(Charge charge) {
        return new ChargeRequestResult(charge.getId(), charge.getTossOrderId(), charge.getAmount(),
            charge.getExpiresAt());
    }
}
