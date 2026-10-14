package com.bbcc.kidly.boundedcontext.settlement.domain;

/** 정산서 상태. PAID만 지급 완료를 뜻한다. */
public enum SettlementStatementStatus {
    CREATED,
    CONFIRMED,
    PAID,
    PAYMENT_FAILED
}
