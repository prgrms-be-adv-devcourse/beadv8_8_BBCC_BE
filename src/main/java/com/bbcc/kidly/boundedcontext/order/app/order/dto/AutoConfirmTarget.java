package com.bbcc.kidly.boundedcontext.order.app.order.dto;

/**
 * 자동 구매확정 대상 주문상품.
 */
public record AutoConfirmTarget(Long orderId, Long orderItemId) {
}
