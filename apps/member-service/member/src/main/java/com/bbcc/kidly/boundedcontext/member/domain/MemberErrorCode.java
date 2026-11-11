package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.member.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    LOGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "로그인할 수 없는 회원입니다", "정지되었거나 탈퇴한 회원입니다."),
    MEMBER_NOT_SUSPENDABLE(HttpStatus.CONFLICT, "정지할 수 없는 회원입니다", "이미 정지 · 탈퇴한 회원입니다."),
    MEMBER_NOT_WITHDRAWABLE(HttpStatus.CONFLICT, "탈퇴할 수 없는 회원입니다", "이미 정지 · 탈퇴한 회원입니다."),
    SELLER_WITHDRAWAL_BLOCKED(HttpStatus.CONFLICT, "탈퇴할 수 없는 판매자입니다", "처리할 항목이 남아 있어요."),
    SELLER_WITHDRAWAL_CHECK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "탈퇴 조건을 확인하지 못했어요", "잠시 뒤 다시 시도해 주세요."),
    DEFAULT_SHIPPING_ADDRESS_NOT_DELETABLE(HttpStatus.CONFLICT, "기본 배송지는 삭제할 수 없습니다", "다른 배송지를 기본으로 바꾼 뒤 삭제해 주세요."),
    NICKNAME_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다", "다른 닉네임을 입력해 주세요."),
    DEAD_LETTER_NOT_FOUND(HttpStatus.NOT_FOUND, "DLT 메시지를 찾을 수 없습니다", "DLT 목록을 새로고침해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
