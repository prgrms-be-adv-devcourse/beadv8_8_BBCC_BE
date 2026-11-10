package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.recommended.RecommendExcludedProducts;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 더보기 목록에서 뺄 상품(회원의 장바구니 상품, 자녀가 산 상품)을 실제 PostgreSQL에서 읽는 동작을 확인한다.
 */
@IntegrationTest
class RecommendExcludedProductsIntegrationTest {

    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 11, 1, 0, 0);

    private static final Long MEMBER_ID = 1L;

    private static final Long CHILD_ID = 7L;

    @Autowired
    private RecommendExcludedProducts excludedProducts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("PREF-08 회원이 기간 안에 담고 빼지 않은 상품은 어느 자녀로 담았든 뺄 상품이다")
    void cartItemsAddedSinceAndNotRemoved() {
        addToCart(MEMBER_ID, null, SINCE, 10L, 1L);
        addToCart(MEMBER_ID, 8L, SINCE.plusHours(1), 11L, 2L);
        addToCart(MEMBER_ID, null, SINCE.plusHours(1), 12L, 3L);
        removeFromCart(MEMBER_ID, SINCE.plusHours(2), 12L, 3L);
        addToCart(MEMBER_ID, null, SINCE.minusSeconds(1), 13L, 4L);
        addToCart(2L, null, SINCE.plusHours(1), 14L, 5L);

        assertThat(excludedProducts.findIds(MEMBER_ID, CHILD_ID, SINCE)).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @DisplayName("PREF-08 같은 자녀가 구매확정한 상품은 언제 샀든 뺄 상품이고, 다른 자녀가 산 상품은 아니다")
    void purchasedProductsOfSameChild() {
        PreferenceTestData.insertPurchase(jdbc, CHILD_ID, 20L, SINCE.minusDays(400));
        PreferenceTestData.insertPurchase(jdbc, 8L, 21L, SINCE.plusHours(1));

        assertThat(excludedProducts.findIds(MEMBER_ID, CHILD_ID, SINCE)).containsExactly(20L);
    }

    private void addToCart(Long memberId, Long childId, LocalDateTime occurredAt, Long productId, Long cartItemId) {
        PreferenceTestData.insertActivity(jdbc, ActivityType.CART_ITEM_ADDED, memberId, childId, occurredAt,
            productId, null, null, cartItemId);
    }

    private void removeFromCart(Long memberId, LocalDateTime occurredAt, Long productId, Long cartItemId) {
        PreferenceTestData.insertActivity(jdbc, ActivityType.CART_ITEM_REMOVED, memberId, null, occurredAt,
            productId, null, null, cartItemId);
    }
}
