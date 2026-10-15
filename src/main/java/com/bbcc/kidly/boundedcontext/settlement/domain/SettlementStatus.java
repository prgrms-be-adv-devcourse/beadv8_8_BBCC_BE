package com.bbcc.kidly.boundedcontext.settlement.domain;

/**
 * 정산서 상태. COMPLETED는 계산을 마치고 지급을 요청한 상태이고, PAID만 지급 완료다.
 */
public enum SettlementStatus {
    COMPLETED,
    PAID,
    FAILED
}
