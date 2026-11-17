package com.bbcc.kidly.boundedcontext.cart.app.dto;

import com.bbcc.kidly.boundedcontext.cart.app.port.CartCatalog;
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
     * @param reserved 다른 곳이 예약한 칸인지(#998). 예약된 칸은 빼거나 수량을 바꿀 수 없다
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
        String sellerName,
        boolean reserved,
        String thumbnailUrl) {

        /**
         * 상품 복사본이 없는 줄: 상품 · 판매자 필드 null, 구매 불가, 금액 0(#1509, null 인자 대신 쓰임새를 이름으로).
         */
        public static Line withoutProduct(Long cartItemId, Long optionId, Long childId, int quantity,
            boolean reserved) {
            return new Line(cartItemId, optionId, null, null, null, null, null, childId, quantity, null, false, 0L,
                null, reserved, null);
        }

        /**
         * 상품 복사본이 있는 줄: 금액은 지금 가격 × 수량(넘치면 ArithmeticException).
         *
         * @param sellerName 판매자 복사본이 없으면 null
         */
        public static Line withProduct(Long cartItemId, Long optionId, Long childId, int quantity, boolean reserved,
            CartCatalog.ProductOption product, String sellerName) {
            return new Line(cartItemId, optionId, product.productId(), product.sellerId(), product.productName(),
                product.optionName(), product.size(), childId, quantity, product.unitPrice(), product.purchasable(),
                Math.multiplyExact(product.unitPrice(), quantity), sellerName, reserved,
                product.thumbnailUrl());
        }
    }
}
