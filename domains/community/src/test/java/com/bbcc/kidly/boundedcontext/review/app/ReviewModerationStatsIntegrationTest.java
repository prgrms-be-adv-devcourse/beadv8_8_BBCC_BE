package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.app.port.in.MemberWithdrawalRecorder;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import com.bbcc.kidly.boundedcontext.review.out.moderation.FakeModerationAdapter;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 집계 정합(가장 위험한 곳): 숨김 · 복원 · 숨김 중 수정 · 삭제 · 탈퇴를 거친 뒤 상품 요약 집계 = 공개 리뷰만 다시 센 값.
 */
@IntegrationTest
class ReviewModerationStatsIntegrationTest {

    private static final long PRODUCT = 14L;
    private static final long ADMIN = 99L;
    private static final long A = 21L;
    private static final long B = 22L;
    private static final long C = 23L;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewModerationService moderationService;

    @Autowired
    private ReviewModerationAdminService adminService;

    @Autowired
    private MemberWithdrawalRecorder withdrawalRecorder;

    @Autowired
    private FakeModerationAdapter fake;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reviews() {
        fake.reset();
    }

    @Test
    @DisplayName("검사 숨김 → 숨김 중 별점 · 핏 수정 → 관리자 복원(그때 값으로 더함) → 삭제")
    void hideEditRestoreDelete() {
        long a = write(A, 5, "SMALL");
        write(B, 3, "LARGE");
        fake.willReturn(Result.flagged(ModerationReason.HATE), Result.passed());
        moderationService.processPending();
        assertStatsMatchPublicRecount();

        reviewService.update(A, a, new ReviewService.ReviewChange(true, 1, false, null,
            ReviewService.FitInput.text("TRUE_TO_SIZE")));
        assertStatsMatchPublicRecount();

        adminService.changeVisibility(ADMIN, a, "VISIBLE");
        assertStatsMatchPublicRecount();
        assertThat(stats()).containsEntry("review_count", 2L).containsEntry("rating_sum", 4L)
            .containsEntry("fit_true_to_size", 1L).containsEntry("fit_small", 0L);

        reviewService.delete(A, a);
        assertStatsMatchPublicRecount();
    }

    @Test
    @DisplayName("관리자 숨김 확정(공개에서) → 수정 → 숨김 중 삭제, 다시 보낸 숨김 · 복원은 한 번만 반영")
    void confirmHideThenDeleteWhileHidden() {
        long a = write(A, 4, "LARGE");
        write(B, 2, null);
        fake.willReturn(Result.passed());
        moderationService.processPending();

        adminService.changeVisibility(ADMIN, a, "HIDDEN");
        adminService.changeVisibility(ADMIN, a, "HIDDEN");
        assertStatsMatchPublicRecount();
        reviewService.update(A, a, new ReviewService.ReviewChange(true, 5, true, "고친 글"));
        assertStatsMatchPublicRecount();
        reviewService.delete(A, a);
        assertStatsMatchPublicRecount();
        assertThat(stats()).containsEntry("review_count", 1L).containsEntry("rating_sum", 2L);
    }

    @Test
    @DisplayName("복원 → 글 수정(다시 대기, 공개 유지) → 검사 숨김 → 복원 · 숨김 확정을 오가도 맞다")
    void restoreThenRecheck() {
        long a = write(A, 5, "SMALL");
        fake.willReturn(Result.flagged(ModerationReason.SEXUAL));
        moderationService.processPending();
        adminService.changeVisibility(ADMIN, a, "VISIBLE");
        assertStatsMatchPublicRecount();

        reviewService.update(A, a, new ReviewService.ReviewChange(false, null, true, "고친 글"));
        assertStatsMatchPublicRecount();
        fake.willReturn(Result.flagged(ModerationReason.SEXUAL));
        moderationService.processPending();
        assertStatsMatchPublicRecount();
        adminService.changeVisibility(ADMIN, a, "VISIBLE");
        adminService.changeVisibility(ADMIN, a, "VISIBLE");
        adminService.changeVisibility(ADMIN, a, "HIDDEN");
        assertStatsMatchPublicRecount();
        adminService.changeVisibility(ADMIN, a, "VISIBLE");
        assertStatsMatchPublicRecount();
        assertThat(stats()).containsEntry("review_count", 1L).containsEntry("fit_small", 1L);
    }

    @Test
    @DisplayName("숨김 리뷰를 쓴 회원이 탈퇴해도(리뷰는 남는다) 집계는 공개 리뷰만 센 값 그대로")
    void withdrawalWithHiddenReview() {
        long a = write(A, 5, null);
        write(C, 4, "LARGE");
        fake.willReturn(Result.flagged(ModerationReason.OTHER), Result.passed());
        moderationService.processPending();

        withdrawalRecorder.record(A, LocalDateTime.of(2026, 11, 6, 10, 0, 0, 123_456_789));

        assertStatsMatchPublicRecount();
        assertThat(jdbcTemplate.queryForObject("select moderation_status from community.review where id = ?",
            String.class, a)).isEqualTo("HIDDEN");
    }

    private long write(long memberId, int rating, String fit) {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) values (?, 12, ?, ?, 15, '110', now(), now(), "
            + "now())", memberId, memberId, PRODUCT);
        return reviewService.write(memberId, "닉", String.valueOf(memberId), rating, "좋아요",
            fit == null ? ReviewService.FitInput.absent() : ReviewService.FitInput.text(fit)).reviewId();
    }

    private Map<String, Object> stats() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select review_count, rating_sum, fit_small, "
            + "fit_true_to_size, fit_large from community.review_stats where product_id = ?", PRODUCT);
        return rows.isEmpty()
            ? Map.of("review_count", 0L, "rating_sum", 0L, "fit_small", 0L, "fit_true_to_size", 0L, "fit_large", 0L)
            : rows.getFirst();
    }

    private void assertStatsMatchPublicRecount() {
        Map<String, Object> recount = jdbcTemplate.queryForMap("""
            select count(*) as review_count, coalesce(sum(rating), 0) as rating_sum,
                   count(*) filter (where fit = 'SMALL') as fit_small,
                   count(*) filter (where fit = 'TRUE_TO_SIZE') as fit_true_to_size,
                   count(*) filter (where fit = 'LARGE') as fit_large
            from community.review
            where product_id = ? and moderation_status in ('PENDING', 'PASSED', 'RESTORED')
            """, PRODUCT);
        assertThat(stats()).isEqualTo(recount);
    }
}
