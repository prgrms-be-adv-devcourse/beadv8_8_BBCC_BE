package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 상품별 리뷰 집계(review_stats): 첫 리뷰에서 행이 생기고, 작성 +1 · +별점, 수정 별점 차이만큼, 삭제 −1 · −별점.
 */
@IntegrationTest
class ReviewStatsIntegrationTest {

    private static final long ME = 13L;
    private static final long PRODUCT = 14L;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("첫 리뷰에서 집계 행이 생기고, 작성 · 수정 · 삭제마다 리뷰 수 · 별점 합이 바뀐다")
    void statsFollowWrites() {
        eligible(101, PRODUCT);
        eligible(102, PRODUCT);
        assertThat(stats()).isNull();

        long first = reviewService.write(ME, "닉", "101", 4, "좋아요").reviewId();
        assertThat(stats()).containsEntry("review_count", 1).containsEntry("rating_sum", 4);

        reviewService.write(ME, "닉", "102", 5, "최고");
        assertThat(stats()).containsEntry("review_count", 2).containsEntry("rating_sum", 9);

        reviewService.update(ME, first, 1, "다시 보니 별로");
        assertThat(stats()).containsEntry("review_count", 2).containsEntry("rating_sum", 6);

        reviewService.delete(ME, first);
        assertThat(stats()).containsEntry("review_count", 1).containsEntry("rating_sum", 5);
    }

    @Test
    @DisplayName("삭제하면 같은 주문상품에 다시 쓸 수 있고, 집계도 다시 더해진다")
    void rewriteAfterDelete() {
        eligible(201, PRODUCT);
        long id = reviewService.write(ME, "닉", "201", 3, "보통").reviewId();
        reviewService.delete(ME, id);

        reviewService.write(ME, "닉", "201", 5, "다시 써요");

        assertThat(stats()).containsEntry("review_count", 1).containsEntry("rating_sum", 5);
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.review where order_item_id = 201",
            Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 값으로 두 번 고쳐도 · 두 번 지워도(두 번째는 404) 집계는 1회분만 바뀐다")
    void repeatedUpdateAndDeleteApplyOnce() {
        eligible(301, PRODUCT);
        long id = reviewService.write(ME, "닉", "301", 3, "보통").reviewId();

        reviewService.update(ME, id, 5, "좋아졌어요");
        reviewService.update(ME, id, 5, "좋아졌어요");
        assertThat(stats()).containsEntry("review_count", 1).containsEntry("rating_sum", 5);

        reviewService.delete(ME, id);
        assertThatThrownBy(() -> reviewService.delete(ME, id)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));
        assertThat(stats()).containsEntry("review_count", 0).containsEntry("rating_sum", 0);
    }

    @Test
    @DisplayName("일부 수정: 본문만 고치면 별점 · 집계 그대로, 별점만 고치면 본문 그대로 · 집계는 차이만큼(DB 확인)")
    void partialUpdateStoredAndCounted() {
        eligible(401, PRODUCT);
        long id = reviewService.write(ME, "닉", "401", 3, "처음 본문").reviewId();

        reviewService.update(ME, id, new ReviewService.ReviewChange(false, null, true, "본문만"));
        assertThat(review(id)).containsEntry("rating", 3).containsEntry("content", "본문만");
        assertThat(stats()).containsEntry("review_count", 1).containsEntry("rating_sum", 3);

        reviewService.update(ME, id, new ReviewService.ReviewChange(true, 5, false, null));
        assertThat(review(id)).containsEntry("rating", 5).containsEntry("content", "본문만");
        assertThat(stats()).containsEntry("review_count", 1).containsEntry("rating_sum", 5);

        reviewService.update(ME, id, new ReviewService.ReviewChange(true, 1, true, "둘 다"));
        assertThat(review(id)).containsEntry("rating", 1).containsEntry("content", "둘 다");
        assertThat(stats()).containsEntry("review_count", 1).containsEntry("rating_sum", 1);
    }

    private Map<String, Object> review(long id) {
        return jdbcTemplate.queryForMap("select rating::int as rating, content from community.review where id = ?",
            id);
    }

    @Test
    @DisplayName("핏 집계(DB): 작성 · 핏 변경 · 같은 변경 두 번 · 지우기 · 삭제 뒤 fit_* = 실제 행별 핏 개수")
    void fitCountsFollowRows() {
        sizedEligible(501, PRODUCT);
        sizedEligible(502, PRODUCT);
        long first = reviewService.write(ME, "닉", "501", 4, "좋아요", ReviewService.FitInput.text("SMALL"))
            .reviewId();
        long second = reviewService.write(ME, "닉", "502", 5, "최고", ReviewService.FitInput.text("SMALL"))
            .reviewId();
        assertFitsMatchRows();
        assertThat(fits()).containsExactly(2L, 0L, 0L);

        ReviewService.ReviewChange toLarge = new ReviewService.ReviewChange(false, null, false, null,
            ReviewService.FitInput.text("LARGE"));
        reviewService.update(ME, first, toLarge);
        reviewService.update(ME, first, toLarge);
        assertThat(fits()).containsExactly(1L, 0L, 1L);

        reviewService.update(ME, second, new ReviewService.ReviewChange(false, null, false, null,
            ReviewService.FitInput.cleared()));
        assertThat(fits()).containsExactly(0L, 0L, 1L);

        reviewService.delete(ME, first);
        assertThat(fits()).containsExactly(0L, 0L, 0L);
        assertFitsMatchRows();
    }

    private List<Long> fits() {
        Map<String, Object> row = jdbcTemplate.queryForMap("select fit_small, fit_true_to_size, fit_large "
            + "from community.review_stats where product_id = ?", PRODUCT);
        return List.of(((Number) row.get("fit_small")).longValue(), ((Number) row.get("fit_true_to_size")).longValue(),
            ((Number) row.get("fit_large")).longValue());
    }

    private void assertFitsMatchRows() {
        assertThat(jdbcTemplate.queryForList("""
            select s.product_id from community.review_stats s
            where s.fit_small <> (select count(*) from community.review r
                                  where r.product_id = s.product_id and r.fit = 'SMALL')
               or s.fit_true_to_size <> (select count(*) from community.review r
                                         where r.product_id = s.product_id and r.fit = 'TRUE_TO_SIZE')
               or s.fit_large <> (select count(*) from community.review r
                                  where r.product_id = s.product_id and r.fit = 'LARGE')
            """)).isEmpty();
    }

    private void sizedEligible(long orderItemId, long productId) {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) "
            + "values (?, 1, ?, ?, 1, '110', now(), now(), now())", orderItemId, ME, productId);
    }

    private void eligible(long orderItemId, long productId) {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) "
            + "values (?, 1, ?, ?, 1, null, now(), now(), now())",
            orderItemId, ME, productId);
    }

    private Map<String, Object> stats() {
        return jdbcTemplate.queryForList("select review_count::int as review_count, rating_sum::int as rating_sum "
            + "from community.review_stats where product_id = ?", PRODUCT).stream().findFirst().orElse(null);
    }
}
