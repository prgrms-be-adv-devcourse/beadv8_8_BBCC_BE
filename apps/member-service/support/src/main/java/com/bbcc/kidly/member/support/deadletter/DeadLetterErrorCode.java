package com.bbcc.kidly.member.support.deadletter;

import com.bbcc.kidly.member.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum DeadLetterErrorCode implements ErrorCode {

    DEAD_LETTER_NOT_FOUND(HttpStatus.NOT_FOUND, "DLT 메시지를 찾을 수 없습니다", "DLT 목록을 새로고침해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
