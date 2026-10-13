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

    APPLICATION_NOT_PENDING(HttpStatus.CONFLICT, "심사 중인 신청이 아닙니다", "이미 처리된 판매자 신청입니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
