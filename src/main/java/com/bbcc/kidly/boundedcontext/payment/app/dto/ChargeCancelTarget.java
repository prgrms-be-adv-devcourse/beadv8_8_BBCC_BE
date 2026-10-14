package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeCancellation;

/**
 * 토스에 취소를 요청할 충전. 취소 요청 ID로 멱등 키를 만들어, 다시 호출해도 토스에서 한 번만 취소된다.
 *
 * @param paymentKeyEnc 암호화된 토스 paymentKey. 토스를 부르기 직전에 복호화한다
 * @param reason 토스 취소 사유
 */
public record ChargeCancelTarget(Long chargeId, Long cancellationId, String paymentKeyEnc, String reason,
                                 long amount) {

    public static ChargeCancelTarget from(Charge charge) {
        ChargeCancellation cancellation = charge.getLastCancellation();
        return new ChargeCancelTarget(charge.getId(), cancellation.getId(), charge.getPaymentKeyEnc(),
            cancellation.getReason(), cancellation.getAmount());
    }
}
