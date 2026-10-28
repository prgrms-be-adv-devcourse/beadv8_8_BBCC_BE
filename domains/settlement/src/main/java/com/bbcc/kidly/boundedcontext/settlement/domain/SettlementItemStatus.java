package com.bbcc.kidly.boundedcontext.settlement.domain;

/**
 * 정산 항목 상태. SETTLED는 정산서에 들어갔다는 뜻이고 지급 완료가 아니다.
 */
public enum SettlementItemStatus {
    PENDING,
    SETTLED
}
