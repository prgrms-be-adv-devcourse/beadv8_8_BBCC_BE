package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;

/**
 * 충전 취소 결과. CANCELLED면 토스 취소까지 끝났고, CANCELLING이면 토스 결과를 아직 모른다.
 *
 * @param status 충전 상태 이름(ChargeStatus)
 * @param balance 취소 후 잔액(원). 결과를 모르면 null
 */
public record ChargeCancelResult(Long chargeId, String status, long amount, Long balance) {

    public static ChargeCancelResult cancelled(Long chargeId, long amount, long balance) {
        return new ChargeCancelResult(chargeId, ChargeStatus.CANCELLED.name(), amount, balance);
    }

    public static ChargeCancelResult cancellingOf(ChargeCancelTarget target) {
        return new ChargeCancelResult(target.chargeId(), ChargeStatus.CANCELLING.name(), target.amount(), null);
    }

    // 토스 결과를 아직 몰라 취소 중으로 남았는지
    public boolean cancelling() {
        return ChargeStatus.CANCELLING.name().equals(status);
    }
}
