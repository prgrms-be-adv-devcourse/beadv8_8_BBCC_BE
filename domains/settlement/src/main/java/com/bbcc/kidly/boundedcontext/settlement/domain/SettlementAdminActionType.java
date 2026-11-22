package com.bbcc.kidly.boundedcontext.settlement.domain;

/**
 * 대상 ID는 행위에 따라 정산서, 정산 실행, DLT의 ID다.
 */
public enum SettlementAdminActionType {
    PAYOUT_RETRY,
    RUN_RECOVER,
    RUN_MANUAL,
    DEAD_LETTER_REPROCESS
}
