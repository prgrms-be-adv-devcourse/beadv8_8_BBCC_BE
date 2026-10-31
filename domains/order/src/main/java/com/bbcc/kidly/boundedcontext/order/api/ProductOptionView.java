package com.bbcc.kidly.boundedcontext.order.api;

/**
 * 상품 옵션 복사본(#617).
 *
 * @param purchasable 지금 살 수 있는지(판매 중 · 옵션 판매 중 · 품절 아님). 주문 복사본이 정한 값 그대로
 */
public record ProductOptionView(
    Long optionId,
    Long productId,
    Long sellerId,
    String productName,
    String optionName,
    String size,
    long unitPrice,
    boolean purchasable) {
}
