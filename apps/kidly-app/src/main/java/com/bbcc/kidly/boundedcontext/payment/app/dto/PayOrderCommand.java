package com.bbcc.kidly.boundedcontext.payment.app.dto;

import java.util.List;

/**
 * 주문 하나의 예치금 결제 요청.
 *
 * @param amount 주문이 요청한 총액(원). 주문상품 금액의 합과 같아야 결제한다
 */
public record PayOrderCommand(Long orderId, Long memberId, long amount, List<Item> items) {

    /**
     * @param amount 주문상품 하나의 금액(원)
     */
    public record Item(Long orderItemId, long amount) {
    }

    public boolean amountMatchesItems() {
        return amount == items.stream().mapToLong(Item::amount).sum();
    }
}
