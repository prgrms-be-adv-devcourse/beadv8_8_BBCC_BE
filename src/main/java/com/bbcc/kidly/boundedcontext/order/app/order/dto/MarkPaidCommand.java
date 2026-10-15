package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import java.util.List;

/**
 * 결제 완료 반영 입력. 결제대기 주문이면 회원 · 주문상품 구성 · 주문상품 금액 · 총액을 주문과 대조한다.
 *
 * @param memberId 결제한 회원
 * @param amount 결제 금액(원)
 * @param items 주문상품별 결제 금액
 */
public record MarkPaidCommand(
    Long orderId,
    Long paymentId,
    Long memberId,
    long amount,
    List<Item> items) {

    public record Item(
        Long orderItemId,
        long amount) {
    }
}
