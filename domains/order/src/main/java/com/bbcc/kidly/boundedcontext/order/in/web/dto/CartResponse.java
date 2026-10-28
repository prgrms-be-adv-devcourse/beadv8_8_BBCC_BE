package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import com.bbcc.kidly.boundedcontext.order.app.cart.dto.CartView;
import java.util.List;

/**
 * 장바구니. 최근에 담은 줄부터.
 *
 * @param totalAmount 구매 가능한 줄의 금액 합
 */
public record CartResponse(List<Line> items, long totalAmount) {

    /**
     * 장바구니 한 줄. ID는 문자열(기획서 6.1). 상품 복사본이 없는 줄은 상품 필드가 null.
     *
     * @param sellerName 판매자의 지금 상호명. 판매자 정보가 아직 없으면 null
     */
    public record Line(
        String cartItemId,
        String optionId,
        String productId,
        String sellerId,
        String productName,
        String optionName,
        String size,
        String childId,
        int quantity,
        Long unitPrice,
        boolean purchasable,
        long lineAmount,
        String sellerName) {

        public static Line from(CartView.Line view) {
            return new Line(
                toText(view.cartItemId()),
                toText(view.optionId()),
                toText(view.productId()),
                toText(view.sellerId()),
                view.productName(),
                view.optionName(),
                view.size(),
                toText(view.childId()),
                view.quantity(),
                view.unitPrice(),
                view.purchasable(),
                view.lineAmount(),
                view.sellerName());
        }

        private static String toText(Long id) {
            return id == null ? null : String.valueOf(id);
        }
    }

    public static CartResponse from(CartView view) {
        return new CartResponse(view.items().stream().map(Line::from).toList(), view.totalAmount());
    }
}
