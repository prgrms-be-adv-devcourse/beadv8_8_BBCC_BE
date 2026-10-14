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
    PRICE_GROUP_MISMATCH(HttpStatus.BAD_REQUEST, "가격 구분 묶음의 가격이 맞지 않습니다", "가격 구분 묶음마다 가격을 하나씩 넣어 주세요."),
    EXISTING_OPTION_PRICE_MISMATCH(HttpStatus.CONFLICT, "이미 있는 옵션과 가격이 다릅니다",
        "같은 가격 구분 묶음의 옵션은 가격이 같아야 합니다. 이미 있는 옵션과 같은 가격을 넣어 주세요."),
    NOT_PRODUCT_OWNER(HttpStatus.FORBIDDEN, "상품을 바꿀 권한이 없습니다", "본인이 등록한 상품만 바꿀 수 있습니다."),
    REQUIRED_OPTION_MISSING(HttpStatus.BAD_REQUEST, "필수 옵션 항목이 비어 있습니다", "필수 옵션 항목마다 값을 하나씩 넣어 주세요."),
    NOT_OPTION_ATTRIBUTE(HttpStatus.BAD_REQUEST, "이 상품 유형의 옵션 항목이 아닙니다", "상품 유형의 옵션 항목 값으로만 옵션을 만들어 주세요."),
    OPTION_VALUE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "허용되지 않은 옵션 값입니다", "이 상품 유형에서 고를 수 있는 옵션 값을 넣어 주세요."),
    DUPLICATE_OPTION(HttpStatus.CONFLICT, "이미 있는 옵션입니다", "같은 값 묶음의 옵션은 하나만 등록할 수 있습니다."),
    OPTION_ATTRIBUTES_MISMATCH(HttpStatus.BAD_REQUEST, "옵션 항목 묶음이 다릅니다", "한 상품의 옵션은 모두 같은 옵션 항목으로 만들어 주세요."),
    SIZE_FREE_MIXED(HttpStatus.BAD_REQUEST, "FREE와 숫자 사이즈를 섞을 수 없습니다", "한 상품의 사이즈는 모두 FREE이거나 모두 숫자여야 합니다."),
    INVALID_HEIGHT_RANGE(HttpStatus.BAD_REQUEST, "권장 키 범위가 올바르지 않습니다",
        "권장 키는 최솟값과 최댓값을 함께 넣고, 최솟값이 최댓값보다 크지 않게 넣어 주세요."),
    DUPLICATE_SIZE_SPEC(HttpStatus.BAD_REQUEST, "같은 사이즈의 실측이 두 번 들어왔습니다", "사이즈마다 실측을 한 번만 넣어 주세요."),
    MEASUREMENT_ITEM_NOT_IN_CATEGORY(HttpStatus.BAD_REQUEST, "카테고리에 없는 실측 항목입니다", "상품 카테고리의 실측 항목만 넣어 주세요."),
    SIZE_SPEC_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "실측을 받지 않는 상품 유형입니다", "사이즈 항목이 있는 상품에만 실측을 넣어 주세요."),
    SIZE_NOT_IN_OPTIONS(HttpStatus.BAD_REQUEST, "옵션에 없는 사이즈입니다", "옵션에 쓴 사이즈마다 실측을 넣어 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
