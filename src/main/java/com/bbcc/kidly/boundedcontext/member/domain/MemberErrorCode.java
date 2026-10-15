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

    LOGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "로그인할 수 없는 회원입니다", "정지되었거나 탈퇴한 회원입니다."),
    MEMBER_NOT_SUSPENDABLE(HttpStatus.CONFLICT, "정지할 수 없는 회원입니다", "이미 정지 · 탈퇴했거나 관리자인 회원입니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인입니다", "로그인 방법을 다시 선택해 주세요."),
    INVALID_AUTHORIZATION_CODE(HttpStatus.BAD_REQUEST, "인가 코드가 올바르지 않습니다", "소셜 로그인을 다시 시도해 주세요."),
    OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "소셜 로그인에 실패했습니다", "잠시 후 다시 시도해 주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다", "다시 로그인해 주세요."),
    DEFAULT_SHIPPING_ADDRESS_NOT_DELETABLE(HttpStatus.CONFLICT, "기본 배송지는 삭제할 수 없습니다", "다른 배송지를 기본으로 바꾼 뒤 삭제해 주세요."),
    NICKNAME_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다", "다른 닉네임을 입력해 주세요."),
    EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "이메일 제공이 필요합니다", "카카오 계정에 이메일을 등록 · 인증한 뒤 다시 로그인해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
