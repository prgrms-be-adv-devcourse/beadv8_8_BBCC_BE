package com.bbcc.kidly.boundedcontext.settlement.domain;

/**
 * 정산 실행 상태. PARTIAL_FAILED는 건너뛴 판매자가 있다는 뜻이고, FAILED는 작업 자체가 실패했다는 뜻이다.
 */
public enum SettlementRunStatus {
    RUNNING,
    COMPLETED,
    PARTIAL_FAILED,
    FAILED
}
