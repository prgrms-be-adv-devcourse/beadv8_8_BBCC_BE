package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 상품 유형을 정의할 때 생기는 업무 오류다.
 * 관리자가 고른 설정 때문에 생기므로 400으로 알린다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ProductTypeErrorCode implements ErrorCode {

    REQUIRED_OPTION_MISSING(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "필수 옵션 항목을 하나 이상 연결해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
