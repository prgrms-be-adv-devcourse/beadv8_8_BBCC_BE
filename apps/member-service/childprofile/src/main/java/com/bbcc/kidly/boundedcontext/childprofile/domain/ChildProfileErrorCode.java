package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.member.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ChildProfileErrorCode implements ErrorCode {
    OPTIONAL_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "선택 동의가 필요합니다", "체형과 선호 핏 입력에는 선택 동의가 필요합니다."),
    CHILD_TOO_OLD(HttpStatus.BAD_REQUEST, "등록할 수 없는 나이입니다", "만 14세 미만 자녀만 등록할 수 있습니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
