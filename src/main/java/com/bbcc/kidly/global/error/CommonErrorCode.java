package com.bbcc.kidly.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다", "요청 값을 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다", "로그인 후 다시 시도해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다", "이 요청을 처리할 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없습니다", "요청한 대상을 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다", "잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
