package com.bbcc.kidly.boundedcontext.payment.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 원장에 기록하는 거래 종류. 입금이면 잔액이 늘고, 출금이면 잔액이 준다.
 */
@Getter
@RequiredArgsConstructor
public enum WalletLedgerType {

    CHARGE(true),
    CHARGE_CANCEL(false),
    // 토스가 충전 취소를 거절해 먼저 뺀 충전액을 되돌린다
    CHARGE_CANCEL_REVERT(true),
    PAYMENT(false),
    REFUND(true),
    SETTLEMENT(true),
    // 탈퇴 회원 지갑의 남은 잔액을 운영자가 환급했다고 정리한다(실제 송금 없음, #731)
    BALANCE_RETURN(false);

    private final boolean deposit;
}
