package com.bbcc.kidly.boundedcontext.settlement.domain;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum SettlementErrorCode implements ErrorCode {

    INVALID_SETTLEMENT_CUTOFF(HttpStatus.BAD_REQUEST, "정산 기준일이 올바르지 않습니다", "오늘 또는 지난 날짜로 지정해 주세요."),
    SETTLEMENT_RUN_IN_PROGRESS(HttpStatus.CONFLICT, "정산이 실행 중입니다", "실행 중인 정산이 끝난 뒤 다시 실행해 주세요."),
    SETTLEMENT_ALREADY_RUN(HttpStatus.CONFLICT, "이미 정산한 기준일입니다", "다른 기준일로 실행해 주세요."),
    SETTLEMENT_NOT_RETRYABLE(
        HttpStatus.CONFLICT, "지급을 다시 요청할 수 없는 정산서입니다", "지급 결과를 받지 못한 정산서(COMPLETED)만 다시 요청할 수 있습니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
