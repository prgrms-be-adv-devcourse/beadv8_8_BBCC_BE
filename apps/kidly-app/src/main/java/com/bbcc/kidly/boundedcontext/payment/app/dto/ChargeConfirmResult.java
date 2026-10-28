package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;

/**
 * 충전 승인 결과. DONE이면 입금이 끝났고, CONFIRMING이면 토스 결과를 아직 모른다.
 *
 * @param status 충전 상태 이름(ChargeStatus)
 * @param balance 입금 후 잔액(원). 결과를 모르면 null
 */
public record ChargeConfirmResult(Long chargeId, String status, long amount, Long balance) {

    public static ChargeConfirmResult done(Long chargeId, long amount, long balance) {
        return new ChargeConfirmResult(chargeId, ChargeStatus.DONE.name(), amount, balance);
    }

    public static ChargeConfirmResult confirmingOf(ChargeConfirmTarget target) {
        return new ChargeConfirmResult(target.chargeId(), ChargeStatus.CONFIRMING.name(), target.amount(), null);
    }

    // 토스 결과를 아직 몰라 승인 중으로 남았는지
    public boolean confirming() {
        return ChargeStatus.CONFIRMING.name().equals(status);
    }
}
