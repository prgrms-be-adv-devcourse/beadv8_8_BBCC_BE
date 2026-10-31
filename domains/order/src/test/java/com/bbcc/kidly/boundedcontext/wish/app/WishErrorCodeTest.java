package com.bbcc.kidly.boundedcontext.wish.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.cart.app.CartErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WishErrorCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"INVALID_QUANTITY", "PRODUCT_NOT_PURCHASABLE", "OWN_PRODUCT", "PRODUCT_OPTION_NOT_FOUND",
        "CHILD_NOT_FOUND"})
    @DisplayName("장바구니와 같은 판단의 오류는 응답(상태 · title · detail)이 장바구니와 같다")
    void sameAsCart(String name) {
        WishErrorCode wish = WishErrorCode.valueOf(name);
        CartErrorCode cart = CartErrorCode.valueOf(name);

        assertThat(wish.status()).isEqualTo(cart.status());
        assertThat(wish.title()).isEqualTo(cart.title());
        assertThat(wish.detail()).isEqualTo(cart.detail());
    }
}
