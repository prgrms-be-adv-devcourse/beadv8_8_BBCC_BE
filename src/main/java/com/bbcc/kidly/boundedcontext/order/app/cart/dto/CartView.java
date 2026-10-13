package com.bbcc.kidly.boundedcontext.order.app.cart.dto;

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
     * @param childId 자녀를 고르지 않았으면 null
     */
    public record Line(
        Long cartItemId,
        Long optionId,
        Long productId,
        String productName,
        String size,
        Long childId,
        int quantity,
        Long unitPrice,
        boolean purchasable,
        long lineAmount) {
    }
}
