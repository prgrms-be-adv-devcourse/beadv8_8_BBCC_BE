package com.bbcc.kidly.boundedcontext.settlement.domain;

/** 정산항목의 상태. SETTLED는 지급 완료가 아닌 정산서 반영을 의미한다. */
public enum SettlementItemStatus {
    PENDING,
    READY,
    SETTLED,
    EXCLUDED
}
