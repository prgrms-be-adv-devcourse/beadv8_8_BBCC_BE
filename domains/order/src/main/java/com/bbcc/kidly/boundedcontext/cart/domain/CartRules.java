package com.bbcc.kidly.boundedcontext.cart.domain;

/**
 * 장바구니에 담을 수 있는 상품 규칙(#642). 상품 · 판매자는 주문 컨텍스트 복사본에서 읽어 값으로 받는다.
 */
public final class CartRules {

    private CartRules() {
    }

    /**
     * 자기 상품(판매자 = 담는 회원)은 담을 수 없다.
     */
    public static void requireNotOwnProduct(Long memberId, Long sellerId) {
        if (sellerId.equals(memberId)) {
            throw new CartRuleViolationException(CartRuleViolationException.Rule.OWN_PRODUCT);
        }
    }
}
