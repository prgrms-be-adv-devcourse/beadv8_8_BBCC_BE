package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 상품 유형을 정의하거나 찾을 때 생기는 업무 오류다.
 * 설정 규칙 위반은 관리자가 고른 설정 때문에 생기므로 400으로 알린다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ProductTypeErrorCode implements ErrorCode {

    REQUIRED_OPTION_MISSING(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "필수 옵션 항목을 하나 이상 연결해 주세요."),
    MULTIPLE_SIZE_ROLES(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "사이즈 역할은 항목 하나에만 둘 수 있습니다."),
    SIZE_ROLE_ON_INFO(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "사이즈 역할은 옵션 항목에만 둘 수 있습니다."),
    PRICE_AFFECTING_ON_INFO(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "가격 구분은 옵션 항목에만 둘 수 있습니다."),
    OPTION_NOT_SINGLE_CHOICE(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "옵션 항목에는 값을 하나 고르는 팔레트나 목록 항목만 연결해 주세요."),
    ALLOWED_VALUES_MISSING(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "모든 값을 허용하지 않는 항목에는 허용 값을 하나 이상 골라 주세요."),
    ALLOWED_VALUE_OF_OTHER_ATTRIBUTE(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "허용 값은 그 항목의 목록 값에서 골라 주세요."),
    ALLOWED_VALUES_ON_ALL_VALUES(HttpStatus.BAD_REQUEST, "상품 유형 설정이 잘못되었습니다", "모든 값을 허용하는 항목에서는 허용 값을 빼 주세요."),
    PRODUCT_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 유형을 찾을 수 없습니다", "상품 유형을 다시 확인해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
