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

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다", "상품을 다시 확인해 주세요."),
    PRODUCT_VERSION_MISMATCH(HttpStatus.CONFLICT, "다른 곳에서 먼저 바뀐 상품입니다", "상품을 다시 불러온 뒤 고쳐 주세요."),
    SALE_INPUTS_REQUIRED_FOR_CATEGORY_CHANGE(HttpStatus.BAD_REQUEST, "카테고리를 바꾸려면 옵션 · 가격 · 실측이 필요합니다",
        "새 카테고리에 맞는 옵션 · 가격 · 실측을 함께 보내 주세요."),
    SALE_INPUTS_INCOMPLETE(HttpStatus.BAD_REQUEST, "옵션 · 가격 · 실측이 함께 오지 않았습니다",
        "옵션 · 가격 · 실측을 모두 보내거나 모두 빼 주세요."),
    SALE_INPUTS_LOCKED(HttpStatus.CONFLICT, "판매를 시작한 상품의 옵션 · 가격 · 실측은 여기서 바꿀 수 없습니다",
        "판매를 시작한 상품은 옵션 · 가격 · 실측을 빼고 보내 주세요."),
    CATEGORY_CHANGE_NOT_ALLOWED(HttpStatus.CONFLICT, "판매를 시작한 상품은 카테고리를 바꿀 수 없습니다",
        "판매를 시작한 상품은 지금 카테고리를 그대로 보내 주세요."),
    OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "옵션을 찾을 수 없습니다", "옵션을 다시 확인해 주세요."),
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
    PRODUCT_VIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "상품을 볼 권한이 없습니다", "본인이 등록한 상품만 볼 수 있습니다."),
    REQUIRED_OPTION_MISSING(HttpStatus.BAD_REQUEST, "필수 옵션 항목이 비어 있습니다", "필수 옵션 항목마다 값을 하나씩 넣어 주세요."),
    NOT_OPTION_ATTRIBUTE(HttpStatus.BAD_REQUEST, "이 상품 유형의 옵션 항목이 아닙니다", "상품 유형의 옵션 항목 값으로만 옵션을 만들어 주세요."),
    OPTION_VALUE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "허용되지 않은 옵션 값입니다", "이 상품 유형에서 고를 수 있는 옵션 값을 넣어 주세요."),
    DUPLICATE_OPTION(HttpStatus.CONFLICT, "이미 있는 옵션입니다", "같은 값 묶음의 옵션은 하나만 등록할 수 있습니다."),
    DUPLICATE_SKU_CODE(HttpStatus.CONFLICT, "이미 쓰는 SKU 코드입니다", "같은 판매자의 다른 옵션과 겹치지 않게 SKU 코드를 넣어 주세요."),
    OPTION_ATTRIBUTES_MISMATCH(HttpStatus.BAD_REQUEST, "옵션 항목 묶음이 다릅니다", "한 상품의 옵션은 모두 같은 옵션 항목으로 만들어 주세요."),
    SIZE_FREE_MIXED(HttpStatus.BAD_REQUEST, "FREE와 숫자 사이즈를 섞을 수 없습니다", "한 상품의 사이즈는 모두 FREE이거나 모두 숫자여야 합니다."),
    INVALID_HEIGHT_RANGE(HttpStatus.BAD_REQUEST, "권장 키 범위가 올바르지 않습니다",
        "권장 키는 최솟값과 최댓값을 함께 넣고, 최솟값이 최댓값보다 크지 않게 넣어 주세요."),
    DUPLICATE_SIZE_SPEC(HttpStatus.BAD_REQUEST, "같은 사이즈의 실측이 겹칩니다", "사이즈마다 실측을 한 번만 넣어 주세요."),
    MEASUREMENT_ITEM_NOT_IN_CATEGORY(HttpStatus.BAD_REQUEST, "카테고리에 없는 실측 항목입니다", "상품 카테고리의 실측 항목만 넣어 주세요."),
    SIZE_SPEC_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "실측을 받지 않는 상품 유형입니다", "사이즈 항목이 있는 상품에만 실측을 넣어 주세요."),
    SIZE_NOT_IN_OPTIONS(HttpStatus.BAD_REQUEST, "옵션에 없는 사이즈입니다", "옵션에 쓴 사이즈의 실측만 넣어 주세요."),
    SALE_START_CONDITION_NOT_MET(HttpStatus.CONFLICT, "판매를 시작할 수 없습니다",
        "옵션을 하나 이상 넣고, 사이즈마다 필수 실측과 권장 키(FREE는 선택)를 채운 뒤 다시 시작해 주세요."),
    SALE_START_INPUT_MISSING(HttpStatus.BAD_REQUEST, "판매 시작에 필요한 값이 빠졌습니다",
        "옵션을 하나 이상 넣고, 사이즈마다 필수 실측과 권장 키(FREE는 선택)를 채워 주세요."),
    SALE_NOT_STARTED(HttpStatus.CONFLICT, "판매를 시작하지 않은 상품입니다", "판매를 시작한 상품만 판매를 중지할 수 있습니다."),
    SALE_STOPPED_BY_ADMIN(HttpStatus.CONFLICT, "관리자가 판매중지한 상품입니다",
        "관리자가 판매중지한 상품은 판매자가 다시 판매를 시작할 수 없습니다. 관리자에게 문의해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
