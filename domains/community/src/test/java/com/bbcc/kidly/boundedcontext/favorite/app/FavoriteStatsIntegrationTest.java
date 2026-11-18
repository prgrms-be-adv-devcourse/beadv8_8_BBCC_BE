package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteOwner;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.fixture.ReviewRows;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 찜 수(favorite_stats): 찜 +1, 다시 찜 그대로, 해제 −1, 없는 찜 해제 그대로, 음수가 되지 않는다.
 */
@IntegrationTest
class FavoriteStatsIntegrationTest {

    private static final long PRODUCT = 14L;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private FavoriteQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("찜 +1, 다시 찜 그대로, 다른 회원 찜 +1, 해제 −1, 없는 찜 해제 그대로(0 밑으로 가지 않음)")
    void countFollowsFavorites() {
        replica(PRODUCT, "ON_SALE");
        assertThat(count()).isZero();

        favoriteService.add(FavoriteOwner.member(1L), "14");
        favoriteService.add(FavoriteOwner.member(1L), "14");
        assertThat(count()).isEqualTo(1);

        favoriteService.add(FavoriteOwner.member(2L), "14");
        assertThat(count()).isEqualTo(2);

        favoriteService.remove(FavoriteOwner.member(1L), "14");
        favoriteService.remove(FavoriteOwner.member(1L), "14");
        assertThat(count()).isEqualTo(1);

        favoriteService.remove(FavoriteOwner.member(2L), "14");
        favoriteService.remove(FavoriteOwner.member(3L), "14");
        assertThat(count()).isZero();
        assertThat(jdbcTemplate.queryForObject("select favorite_count from community.favorite_stats "
            + "where product_id = ?", Long.class, PRODUCT)).isZero();
    }

    private long count() {
        return queryService.summary(String.valueOf(PRODUCT)).favoriteCount();
    }

    private void replica(long productId, String status) {
        new ReviewRows(jdbcTemplate).replicaProduct(productId).status(status).insert();
    }
}
