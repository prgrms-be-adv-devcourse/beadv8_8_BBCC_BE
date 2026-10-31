package com.bbcc.kidly.boundedcontext.cart.app.dto;

import java.util.List;

/**
 * 장바구니. 최근에 담은 줄부터.
 *
 * @param totalAmount 구매 가능한 줄의 금액 합
 */
public record CartView(List<Line> items, long totalAmount) {

    /**
     * 장바구니 한 줄. 가격 · 구매 가능 여부는 상품 복사본의 지금 값이다.
     * 상품 복사본이 없는 줄은 상품 필드가 null, purchasable false, lineAmount 0이다.
     *
     * @param sellerId 상품 복사본의 판매자 ID. 복사본이 없으면 null
     * @param childId 자녀를 고르지 않았으면 null
     * @param sellerName 판매자의 지금 상호명(판매자 복사본). 상품 · 판매자 복사본이 없으면 null
     */
    public record Line(
        Long cartItemId,
        Long optionId,
        Long productId,
        Long sellerId,
        String productName,
        String optionName,
        String size,
        Long childId,
        int quantity,
        Long unitPrice,
        boolean purchasable,
        long lineAmount,
        String sellerName) {
    }
}
