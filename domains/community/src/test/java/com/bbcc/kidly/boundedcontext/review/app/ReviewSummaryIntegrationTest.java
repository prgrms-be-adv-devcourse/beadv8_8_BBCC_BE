package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 상품 리뷰 요약은 집계(review_stats)를 읽는다: 작성 · 수정 · 삭제 뒤 수 · 평균이 맞고, 리뷰가 없으면 0 · null.
 */
@IntegrationTest
class ReviewSummaryIntegrationTest {

    private static final long ME = 13L;
    private static final long PRODUCT = 14L;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("작성 · 수정 · 삭제 뒤 요약이 집계와 맞고, 모두 지우면 0 · null")
    void summaryFollowsWrites() {
        for (long item = 101; item <= 103; item++) {
            jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, "
                + "product_id, option_id, size, confirmed_at, created_at, updated_at) "
                + "values (?, 1, ?, ?, 1, null, now(), now(), now())", item, ME, PRODUCT);
        }
        long first = reviewService.write(ME, "닉", "101", 5, "최고").reviewId();
        long second = reviewService.write(ME, "닉", "102", 4, "좋아요").reviewId();
        long third = reviewService.write(ME, "닉", "103", 4, "좋아요").reviewId();
        assertThat(summary()).isEqualTo(new ReviewQueryService.ProductSummary(PRODUCT, 3, new BigDecimal("4.3")));

        reviewService.update(ME, second, 5, "더 좋아요");
        assertThat(summary()).isEqualTo(new ReviewQueryService.ProductSummary(PRODUCT, 3, new BigDecimal("4.7")));

        reviewService.delete(ME, first);
        assertThat(summary()).isEqualTo(new ReviewQueryService.ProductSummary(PRODUCT, 2, new BigDecimal("4.5")));

        reviewService.delete(ME, second);
        reviewService.delete(ME, third);
        assertThat(summary()).isEqualTo(new ReviewQueryService.ProductSummary(PRODUCT, 0, null));
    }

    @Test
    @DisplayName("리뷰가 한 번도 없는 상품은 0 · null")
    void noReviews() {
        assertThat(queryService.summary("999")).isEqualTo(new ReviewQueryService.ProductSummary(999L, 0, null));
    }

    private ReviewQueryService.ProductSummary summary() {
        return queryService.summary(String.valueOf(PRODUCT));
    }
}
