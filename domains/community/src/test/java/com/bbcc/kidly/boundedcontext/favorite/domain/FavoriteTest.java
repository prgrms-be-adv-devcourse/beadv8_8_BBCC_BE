package com.bbcc.kidly.boundedcontext.favorite.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FavoriteTest {

    @Test
    @DisplayName("판매 중 · 품절 상품은 찜할 수 있다")
    void create() {
        Favorite favorite = Favorite.create(1L, 14L, false);

        assertThat(favorite.getMemberId()).isEqualTo(1L);
        assertThat(favorite.getProductId()).isEqualTo(14L);
    }

    @Test
    @DisplayName("판매 종료 상품은 새로 찜할 수 없다")
    void saleEndedRejected() {
        assertThatThrownBy(() -> Favorite.create(1L, 14L, true))
            .isInstanceOfSatisfying(FavoriteRuleException.class,
                e -> assertThat(e.rule()).isEqualTo(FavoriteRule.SALE_ENDED));
    }
}
