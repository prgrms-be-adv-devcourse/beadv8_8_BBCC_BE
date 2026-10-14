package com.bbcc.kidly.boundedcontext.seller.domain;

import com.bbcc.kidly.global.error.ErrorCode;
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
    SELLER_ALREADY_SUSPENDED(HttpStatus.CONFLICT, "이미 정지된 판매자입니다", "자격이 이미 정지된 판매자입니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
