package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import java.time.LocalDateTime;

/**
 * 자녀를 위해 산 주문상품 한 줄(#1000, BEG-07). 가격 · 배송지 · 판매자 · 주문 ID는 담지 않는다. 출처(조르기로 샀는지)는 주문이 모르고
 * 화면이 조르기 응답의 결제된 주문상품 ID와 맞춰 붙인다.
 *
 * @param status PAID(결제 완료) · SHIPPING(배송 중) · DELIVERED(배송 완료) · CONFIRMED(구매확정) · CANCELLED(취소, 결제 뒤 취소 포함)
 */
public record ChildPurchase(
    Long orderItemId,
    LocalDateTime orderedAt,
    Long productId,
    String productName,
    String optionName,
    int quantity,
    String status) {

    public static final String PAID = "PAID";
    public static final String SHIPPING = "SHIPPING";
    public static final String DELIVERED = "DELIVERED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CANCELLED = "CANCELLED";
}
