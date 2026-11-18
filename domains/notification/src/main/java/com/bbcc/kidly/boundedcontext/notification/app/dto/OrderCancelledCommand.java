package com.bbcc.kidly.boundedcontext.notification.app.dto;

/**
 * @param reason 주문의 취소 사유 이름(PAYMENT_FAILED · PAYMENT_EXPIRED · OUT_OF_STOCK · BUYER_CANCELLED)
 */
public record OrderCancelledCommand(Long orderId, Long memberId, String reason) {
}
