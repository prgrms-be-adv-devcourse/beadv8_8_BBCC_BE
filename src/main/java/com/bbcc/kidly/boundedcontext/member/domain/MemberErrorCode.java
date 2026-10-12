package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    LOGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "로그인할 수 없는 회원입니다", "정지되었거나 탈퇴한 회원입니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
