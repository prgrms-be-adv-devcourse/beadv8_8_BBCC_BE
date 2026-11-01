package com.bbcc.kidly.boundedcontext.cart.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.cart.domain.CartRuleViolationException.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 장바구니에 담을 수 있는 상품 규칙(#642).
 */
class CartRulesTest {

    @Test
    @DisplayName("자기 상품(판매자 = 담는 회원)은 담을 수 없다")
    void ownProduct() {
        assertThatThrownBy(() -> CartRules.requireNotOwnProduct(7L, 7L))
            .isInstanceOf(CartRuleViolationException.class)
            .extracting("rule").isEqualTo(Rule.OWN_PRODUCT);
    }

    @Test
    @DisplayName("다른 판매자의 상품은 담을 수 있다")
    void otherSellersProduct() {
        assertThatCode(() -> CartRules.requireNotOwnProduct(7L, 8L)).doesNotThrowAnyException();
    }
}
