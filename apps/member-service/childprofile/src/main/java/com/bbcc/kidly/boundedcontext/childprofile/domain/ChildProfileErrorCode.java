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
    CHILD_TOO_OLD(HttpStatus.BAD_REQUEST, "등록할 수 없는 나이입니다", "만 14세 미만 자녀만 등록할 수 있습니다."),
    GUARDIAN_UNDERAGE(HttpStatus.BAD_REQUEST, "인증할 수 없는 나이입니다", "만 19세 이상만 보호자로 인증할 수 있습니다."),
    VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN, "본인인증이 필요합니다", "자녀 등록 전에 보호자 본인인증이 필요합니다."),
    VERIFICATION_INCOMPLETE(HttpStatus.BAD_REQUEST, "본인인증이 완료되지 않았습니다", "완료된 본인인증을 제출해 주세요."),
    ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 본인인증했습니다", "이미 본인인증한 회원입니다."),
    IDENTITY_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 인증된 사람입니다", "다른 회원이 이미 인증한 사람입니다."),
    VERIFICATION_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "본인인증 제공자 오류", "본인인증 정보를 확인할 수 없습니다."),
    DEAD_LETTER_NOT_FOUND(HttpStatus.NOT_FOUND, "DLT 메시지를 찾을 수 없습니다", "DLT 목록을 새로고침해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
