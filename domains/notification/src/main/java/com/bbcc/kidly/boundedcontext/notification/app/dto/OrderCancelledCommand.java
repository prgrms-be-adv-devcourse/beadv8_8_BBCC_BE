package com.bbcc.kidly.boundedcontext.notification.app.dto;

/**
 * 구매자 알림함에 넣을 주문 전체 취소.
 *
 * @param reason 주문의 취소 사유 이름(PAYMENT_FAILED · PAYMENT_EXPIRED · OUT_OF_STOCK · BUYER_CANCELLED)
 */
public record OrderCancelledCommand(Long orderId, Long memberId, String reason) {
}
