package com.bbcc.kidly.boundedcontext.settlement.domain;

/**
 * 판매자를 건너뛴 단계. PROCESS = 정산 계획을 만들다 실패, WRITE = 정산서를 저장하다 실패.
 */
public enum SettlementSkipPhase {
    PROCESS,
    WRITE
}
