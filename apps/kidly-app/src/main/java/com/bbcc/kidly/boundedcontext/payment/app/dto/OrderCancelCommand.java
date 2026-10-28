package com.bbcc.kidly.boundedcontext.payment.app.dto;

/**
 * 취소된 주문 하나의 환불 · 결제 거절 요청.
 *
 * @param reason 주문이 보낸 취소 사유 이름. 결제는 기록만 하고 판단에 쓰지 않는다
 */
public record OrderCancelCommand(Long orderId, Long memberId, String reason) {
}
