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

    REQUIRED_OPTION_MISSING(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "필수 옵션 항목을 하나 이상 연결해 주세요."),
    MULTIPLE_SIZE_ROLES(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "사이즈 역할은 항목 하나에만 둘 수 있습니다."),
    SIZE_ROLE_ON_INFO(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "사이즈 역할은 옵션 항목에만 둘 수 있습니다."),
    PRICE_AFFECTING_ON_INFO(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "가격 구분은 옵션 항목에만 둘 수 있습니다."),
    OPTION_NOT_SINGLE_CHOICE(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "옵션 항목은 팔레트나 목록 항목만 될 수 있습니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
