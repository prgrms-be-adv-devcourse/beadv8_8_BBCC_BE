package com.bbcc.kidly.boundedcontext.settlement.domain;

/**
 * 정산 실행 방식. SCHEDULED는 매월 자동 실행, MANUAL은 관리자 수동 실행이다.
 */
public enum SettlementTriggerType {
    SCHEDULED,
    MANUAL
}
