package com.bbcc.kidly.boundedcontext.order.domain;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 주문 모듈(주문 · 장바구니 · 배송) 오류. 문구는 도메인 구현 때 다듬는다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    EMPTY_ORDER_ITEMS(HttpStatus.BAD_REQUEST, "주문상품이 없습니다", "주문할 상품을 하나 이상 골라 주세요."),
    DUPLICATE_ORDER_ITEM(HttpStatus.BAD_REQUEST, "같은 주문상품이 두 번 있습니다", "같은 옵션 · 자녀 조합은 한 줄로 주문해 주세요."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "잘못된 수량입니다", "수량은 1 이상 99 이하여야 합니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "잘못된 금액입니다", "금액은 0 이상이어야 합니다."),
    PRICE_CHANGED(HttpStatus.CONFLICT, "가격이 바뀌었습니다", "상품 가격이 바뀌었습니다. 다시 확인해 주세요."),
    PRODUCT_NOT_PURCHASABLE(HttpStatus.CONFLICT, "구매할 수 없는 상품입니다", "판매 중인 상품이 아닙니다."),
    OWN_PRODUCT(HttpStatus.FORBIDDEN, "자기 상품은 살 수 없습니다", "판매자는 자기 상품을 담거나 주문할 수 없습니다."),
    CHILD_NOT_ORDERABLE(HttpStatus.CONFLICT, "선택할 수 없는 자녀입니다", "자녀 정보를 다시 확인해 주세요."),
    NOT_DELIVERED(HttpStatus.CONFLICT, "배송 완료 전입니다", "배송이 완료된 뒤 구매확정할 수 있습니다."),
    INVALID_DELIVERY_STATUS(HttpStatus.CONFLICT, "처리할 수 없는 배송 상태입니다", "현재 배송 상태에서는 요청을 처리할 수 없습니다."),
    PRODUCT_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 옵션을 찾을 수 없습니다", "상품 옵션을 다시 확인해 주세요."),
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "자녀를 찾을 수 없습니다", "자녀 정보를 다시 확인해 주세요."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 상품을 찾을 수 없습니다", "장바구니를 다시 확인해 주세요."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다", "주문 번호를 다시 확인해 주세요."),
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "판매 주문을 찾을 수 없습니다", "판매 주문 번호를 다시 확인해 주세요."),
    INVALID_CARRIER(HttpStatus.BAD_REQUEST, "지원하지 않는 택배사입니다", "택배사를 다시 확인해 주세요."),
    ORDER_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "주문상품을 찾을 수 없습니다", "주문상품 번호를 다시 확인해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
