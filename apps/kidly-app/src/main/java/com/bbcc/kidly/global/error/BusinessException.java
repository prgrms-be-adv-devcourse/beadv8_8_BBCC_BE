package com.bbcc.kidly.global.error;

import lombok.Getter;

/**
 * 업무 규칙 위반. GlobalExceptionHandler가 ErrorCode로 ProblemDetail을 만든다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.detail());
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
