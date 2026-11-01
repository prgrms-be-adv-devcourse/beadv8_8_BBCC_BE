package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 조르기 오류(#640). 장바구니와 같은 판단(상품 옵션 · 자기 상품 · 구매 가능 · 자녀 · 수량)은 CartErrorCode의 같은 이름 항목과
 * 응답(상태 · title · detail)이 같다(#617 컨텍스트별 복사).
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum WishErrorCode implements ErrorCode {

    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "잘못된 수량입니다", "수량은 1 이상 99 이하여야 합니다."),
    PRODUCT_NOT_PURCHASABLE(HttpStatus.CONFLICT, "구매할 수 없는 상품입니다",
        "판매가 중지됐거나 품절된 상품이 있습니다. 장바구니에서 빼고 다시 주문해 주세요."),
    OWN_PRODUCT(HttpStatus.FORBIDDEN, "자기 상품은 살 수 없습니다",
        "자기 상품은 담거나 주문할 수 없습니다. 다른 판매자의 상품을 골라 주세요."),
    PRODUCT_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 옵션을 찾을 수 없습니다",
        "없는 상품 옵션입니다. 상품 화면에서 옵션을 다시 골라 주세요."),
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "자녀를 찾을 수 없습니다", "없는 자녀입니다. 자녀를 다시 선택해 주세요."),
    WISH_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "조르기 상품을 찾을 수 없습니다",
        "이미 조르기에서 빠진 상품입니다. 목록을 새로고침해 주세요."),
    WISH_ITEM_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "조르기에 더 담을 수 없습니다",
        "조르기에 담을 수 있는 상품 수를 넘었습니다. 다른 상품을 빼고 다시 담아 주세요."),
    WISH_ITEM_REMOVE_FORBIDDEN(HttpStatus.FORBIDDEN, "이 상품은 뺄 수 없습니다",
        "다른 사람이 담은 상품은 뺄 수 없습니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
