package com.bbcc.kidly.boundedcontext.order.app.cart.dto;

/**
 * 장바구니 담기 결과.
 *
 * @param created 새 줄이면 true, 같은 줄에 수량을 더했으면 false
 */
public record AddCartItemResult(CartView.Line line, boolean created) {
}
