package com.bbcc.kidly.boundedcontext.cart.app;

import com.bbcc.kidly.boundedcontext.cart.app.dto.CartView;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartCatalog.ProductOption;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem;

/**
 * 장바구니 줄 응답 값 만들기. 담기 · 수량 변경(CartService)과 조회(CartQueryService)가 같은 규칙으로 만들도록 한 곳에 둔다
 * (#1508, 도우미를 서비스마다 복사하지 않는다). 상태가 없어 빈이 아니다.
 */
final class CartLines {

    private CartLines() {
    }

    /**
     * 상품 복사본이 없으면 상품 필드 null, 구매 불가, 금액 0.
     */
    static CartView.Line toLine(CartItem item, ProductOption product, String sellerName) {
        if (product == null) {
            return CartView.Line.withoutProduct(item.getId(), item.getOptionId(), item.getChildId(), item.getQuantity(),
                item.isReserved());
        }
        return CartView.Line.withProduct(item.getId(), item.getOptionId(), item.getChildId(), item.getQuantity(),
            item.isReserved(), product, sellerName);
    }
}
