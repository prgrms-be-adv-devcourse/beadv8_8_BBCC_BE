package com.bbcc.kidly.boundedcontext.settlement.domain;

/**
 * 감사 기록에 남기는 정산 관리자 행위. 대상 ID는 행위마다 정산서 · 실행 · DLT ID다.
 */
public enum SettlementAdminActionType {
    PAYOUT_RETRY,
    RUN_RECOVER,
    RUN_MANUAL,
    DEAD_LETTER_REPROCESS
}
