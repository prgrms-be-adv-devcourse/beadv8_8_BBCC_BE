package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    MULTIPLE_VALUES_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "값을 하나만 고를 수 있는 항목입니다", "이 항목은 값을 하나만 고를 수 있습니다."),
    DUPLICATE_INFO_VALUE(HttpStatus.BAD_REQUEST, "같은 정보 값이 두 번 들어왔습니다", "같은 항목이나 값은 한 번만 넣을 수 있습니다."),
    REQUIRED_INFO_MISSING(HttpStatus.BAD_REQUEST, "필수 정보 항목이 비어 있습니다", "필수 정보 항목에 값을 넣어 주세요."),
    INFO_VALUE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "허용되지 않은 값입니다", "이 상품 유형에서 고를 수 있는 값을 넣어 주세요."),
    NOT_INFO_ATTRIBUTE(HttpStatus.BAD_REQUEST, "이 상품 유형의 정보 항목이 아닙니다", "상품 유형의 정보 항목에만 값을 넣어 주세요."),
    INPUT_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "입력 방식에 맞지 않는 값입니다", "목록 항목은 값을 고르고, 직접 입력 항목은 글자를 적어 주세요."),
    PRICE_GROUP_MISMATCH(HttpStatus.BAD_REQUEST, "가격 구분 묶음의 가격이 맞지 않습니다", "가격 구분 묶음마다 가격을 하나씩 넣어 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
