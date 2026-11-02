package com.bbcc.kidly.boundedcontext.payment.app.dto;

import java.util.List;

/**
 * 판매자가 취소한 주문상품들의 환불 요청(order-item.cancelled, #711).
 *
 * @param cancellationId 주문의 취소 ID. 취소마다 한 번만 환불한다
 * @param reason 주문이 보낸 취소 사유 이름. 결제는 기록만 하고 판단에 쓰지 않는다
 */
public record OrderItemCancelCommand(Long orderId, Long memberId, Long cancellationId, String reason,
    List<Item> items) {

    /**
     * @param amount 환불할 금액(원). 주문이 보낸 주문상품 금액이다
     */
    public record Item(Long orderItemId, long amount) {
    }
}
