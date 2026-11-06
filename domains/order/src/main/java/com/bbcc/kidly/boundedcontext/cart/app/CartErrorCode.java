package com.bbcc.kidly.boundedcontext.cart.app;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 장바구니 오류. #617 컨텍스트별 복사: 주문과 같이 쓰는 항목은 주문 OrderErrorCode의 같은 이름 항목과 응답(상태 · title ·
 * detail)이 같고, CART_ITEM_NOT_FOUND는 주문에서 옮겼다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CartErrorCode implements ErrorCode {

    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "잘못된 수량입니다", "수량은 1 이상 99 이하여야 합니다."),
    PRODUCT_NOT_PURCHASABLE(HttpStatus.CONFLICT, "구매할 수 없는 상품입니다",
        "판매가 중지됐거나 품절된 상품이 있습니다. 장바구니에서 빼고 다시 주문해 주세요."),
    OWN_PRODUCT(HttpStatus.FORBIDDEN, "자기 상품은 살 수 없습니다",
        "자기 상품은 담거나 주문할 수 없습니다. 다른 판매자의 상품을 골라 주세요."),
    PRODUCT_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 옵션을 찾을 수 없습니다",
        "없는 상품 옵션입니다. 상품 화면에서 옵션을 다시 골라 주세요."),
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "자녀를 찾을 수 없습니다", "없는 자녀입니다. 자녀를 다시 선택해 주세요."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 상품을 찾을 수 없습니다",
        "이미 장바구니에서 빠진 상품입니다. 장바구니를 새로고침해 주세요."),
    // #998 주인별 장바구니 · 아이 장바구니 · 칸 예약
    CART_FORBIDDEN(HttpStatus.FORBIDDEN, "다른 사람의 장바구니입니다", "자기 장바구니만 보고 바꿀 수 있습니다."),
    CHILD_ACCOUNT_FORBIDDEN(HttpStatus.FORBIDDEN, "장바구니를 쓸 수 없는 자녀 계정입니다",
        "자녀 계정 연결을 확인할 수 없습니다. 다시 로그인해 주세요."),
    CHILD_CART_CHILD_FIXED(HttpStatus.BAD_REQUEST, "다른 자녀를 고를 수 없습니다",
        "자녀 장바구니에는 그 자녀가 입을 상품만 담을 수 있습니다."),
    CART_ITEM_RESERVED(HttpStatus.CONFLICT, "예약된 장바구니 상품입니다",
        "다른 곳에서 결제를 기다리는 상품이라 빼거나 수량을 바꿀 수 없습니다."),
    RESERVATION_EXPIRED(HttpStatus.CONFLICT, "결제 기한이 지난 예약입니다",
        "결제할 수 있는 기한이 지난 상품입니다. 장바구니를 새로고침해 주세요."),
    INVALID_RESERVATION(HttpStatus.BAD_REQUEST, "잘못된 예약입니다", "예약할 장바구니 상품과 결제할 사람이 있어야 합니다."),
    CART_ITEM_NOT_ORDERABLE(HttpStatus.FORBIDDEN, "주문할 수 없는 장바구니 상품입니다",
        "자기 장바구니 상품이나 결제를 허락받은 상품만 주문할 수 있습니다."),
    RESERVED_QUANTITY_EXCEEDED(HttpStatus.CONFLICT, "예약된 수량보다 많이 주문할 수 없습니다",
        "결제를 허락받은 장바구니 상품은 담긴 수량까지만 주문할 수 있습니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
