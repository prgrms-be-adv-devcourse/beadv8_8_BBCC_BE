package com.bbcc.kidly.boundedcontext.settlement.domain;

/**
 * 판매자 탈퇴를 막는 정산 쪽 사유.
 * UNSETTLED_ITEM은 구매확정됐지만 정산서에 아직 안 들어간 항목, UNPAID_SETTLEMENT는 지급되지 않은(지급 대기 · 실패) 정산서다.
 */
public enum SellerWithdrawalBlockerType {
    UNSETTLED_ITEM,
    UNPAID_SETTLEMENT
}
