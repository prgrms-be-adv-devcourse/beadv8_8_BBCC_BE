package com.bbcc.kidly.shared.payment.domain;

// 예치금 결제 실패 사유. 결제 엔티티와 PaymentFailedEvent가 함께 쓴다
public enum PaymentFailureCode {
    INSUFFICIENT_BALANCE,
    WALLET_NOT_FOUND,
    // 요청 총액이 주문상품 금액의 합과 다르다
    AMOUNT_MISMATCH
}
