package com.bbcc.kidly.boundedcontext.order.app.order.dto;

/**
 * 주문하기 결과(#351). created가 false면 같은 요청 키로 이미 만든 주문의 지금 상세다.
 */
public record PlaceOrderResult(OrderDetail detail, boolean created) {
}
