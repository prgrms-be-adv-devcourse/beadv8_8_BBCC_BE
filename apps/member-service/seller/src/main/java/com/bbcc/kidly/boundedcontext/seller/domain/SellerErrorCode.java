package com.bbcc.kidly.boundedcontext.seller.domain;

import com.bbcc.kidly.member.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum SellerErrorCode implements ErrorCode {

    ALREADY_SELLER(HttpStatus.CONFLICT, "이미 판매자입니다", "이미 판매자로 등록된 회원입니다."),
    APPLICATION_ALREADY_PENDING(HttpStatus.CONFLICT, "심사 중인 신청이 있습니다", "심사 중인 판매자 신청이 이미 있습니다."),
    APPLICATION_NOT_PENDING(HttpStatus.CONFLICT, "심사 중인 신청이 아닙니다", "이미 처리된 판매자 신청입니다."),
    APPLICANT_WITHDRAWN(HttpStatus.CONFLICT, "탈퇴한 회원의 신청입니다", "신청한 회원이 탈퇴해 승인할 수 없습니다."),
    SELLER_ALREADY_SUSPENDED(HttpStatus.CONFLICT, "이미 정지된 판매자입니다", "자격이 이미 정지된 판매자입니다."),
    DEAD_LETTER_NOT_FOUND(HttpStatus.NOT_FOUND, "DLT 메시지를 찾을 수 없습니다", "DLT 목록을 새로고침해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
