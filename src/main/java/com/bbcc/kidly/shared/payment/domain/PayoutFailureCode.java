package com.bbcc.kidly.shared.payment.domain;

// 정산 지급 실패 사유. PaymentPayoutFailedEvent가 쓴다
public enum PayoutFailureCode {
    // 판매자 회원의 예치금 지갑이 없다
    WALLET_NOT_FOUND
}
