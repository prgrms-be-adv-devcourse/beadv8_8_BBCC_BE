package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService.FitInput;
import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ImageUrlsInput;
import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ReviewChange;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import com.bbcc.kidly.boundedcontext.review.out.moderation.FakeModerationAdapter;
import com.bbcc.kidly.boundedcontext.review.out.storage.FakeReviewImageUploadChecker;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 리뷰 수정 시각(updated_at)은 작성자 수정(별점 · 핏 · 본문 · 사진)일 때만 바뀐다. 검사 반영 · 관리자 조치는 그대로 둔다(#1308).
 * 기준 시각을 과거 고정값으로 넣은 뒤 각 처리를 하고 그 값이 남는지 본다.
 */
@IntegrationTest
class ReviewUpdatedAtIntegrationTest {

    private static final long ME = 21L;
    private static final long PRODUCT = 14L;
    private static final long ADMIN = 99L;
    private static final LocalDateTime BEFORE = LocalDateTime.of(2026, 11, 1, 10, 0, 0, 123_456_000);

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewModerationService moderationService;

    @Autowired
    private ReviewModerationAdminService adminService;

    @Autowired
    private ReviewImageStorage imageStorage;

    @Autowired
    private FakeModerationAdapter fake;

    @Autowired
    private FakeReviewImageUploadChecker uploadChecker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        fake.reset();
        uploadChecker.reset();
    }

    @Test
    @DisplayName("검사 통과는 updated_at을 바꾸지 않는다")
    void passKeeps() {
        long id = writeAged();
        fake.willReturn(Result.passed());

        moderationService.processPending();

        assertThat(status(id)).isEqualTo("PASSED");
        assertThat(updatedAt(id)).isEqualTo(BEFORE);
    }

    @Test
    @DisplayName("검사 걸림(숨김)은 updated_at을 바꾸지 않는다")
    void hideKeeps() {
        long id = writeAged();
        fake.willReturn(Result.flagged(ModerationReason.HATE));

        moderationService.processPending();

        assertThat(status(id)).isEqualTo("HIDDEN");
        assertThat(updatedAt(id)).isEqualTo(BEFORE);
    }

    @Test
    @DisplayName("판단 못 함(시도 +1)은 updated_at을 바꾸지 않는다")
    void undecidedKeeps() {
        long id = writeAged();
        fake.willReturn(Result.undecided(FailureCause.UNAVAILABLE));

        moderationService.processPending();

        assertThat(jdbcTemplate.queryForObject("select moderation_attempts from community.review where id = ?",
            Integer.class, id)).isEqualTo(1);
        assertThat(updatedAt(id)).isEqualTo(BEFORE);
    }

    @Test
    @DisplayName("관리자 숨김 확정 · 다시 공개는 updated_at을 바꾸지 않는다")
    void adminKeeps() {
        long id = writeAged();

        adminService.changeVisibility(ADMIN, id, "HIDDEN");
        assertThat(status(id)).isEqualTo("CONFIRMED_HIDDEN");
        assertThat(updatedAt(id)).isEqualTo(BEFORE);

        adminService.changeVisibility(ADMIN, id, "VISIBLE");
        assertThat(status(id)).isEqualTo("RESTORED");
        assertThat(updatedAt(id)).isEqualTo(BEFORE);
    }

    @Test
    @DisplayName("작성자 수정(별점 · 본문 · 핏 · 사진)은 updated_at을 지금으로 바꾸고, 응답 updatedAt은 DB 값과 같다")
    void authorEditUpdates() {
        String image = imageStorage.ownImageUrlPrefix(ME) + "0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f.jpg";
        uploadChecker.markUploaded(image);
        List<Consumer<Long>> edits = List.of(
            id -> reviewService.update(ME, id, new ReviewChange(true, 2, false, null)),
            id -> reviewService.update(ME, id, new ReviewChange(false, null, true, "바뀐 본문")),
            id -> reviewService.update(ME, id, new ReviewChange(false, null, false, null, FitInput.text("LARGE"))),
            id -> reviewService.update(ME, id, new ReviewChange(false, null, false, null, FitInput.absent(),
                ImageUrlsInput.of(List.of(image)))));
        long orderItem = 40;
        for (Consumer<Long> edit : edits) {
            long id = writeAged(orderItem++);
            LocalDateTime start = LocalDateTime.now().minusSeconds(5);

            edit.accept(id);

            assertThat(updatedAt(id)).isAfter(start);
        }
        long id = writeAged(orderItem);
        ReviewService.ReviewView view = reviewService.update(ME, id, new ReviewChange(true, 1, false, null));
        assertThat(view.updatedAt()).isEqualTo(updatedAt(id)).isNotEqualTo(BEFORE);
    }

    @Test
    @DisplayName("숨김 리뷰도 작성자가 고치면 updated_at이 바뀐다")
    void hiddenAuthorEditUpdates() {
        long id = writeAged();
        adminService.changeVisibility(ADMIN, id, "HIDDEN");

        reviewService.update(ME, id, new ReviewChange(false, null, true, "고친 글"));

        assertThat(updatedAt(id)).isAfter(BEFORE);
    }

    @Test
    @DisplayName("같은 값으로 보낸 수정은 updated_at을 바꾸지 않는다")
    void sameValueKeeps() {
        long id = writeAged();

        reviewService.update(ME, id, new ReviewChange(true, 4, true, "좋은 옷"));

        assertThat(updatedAt(id)).isEqualTo(BEFORE);
    }

    @Test
    @DisplayName("같은 사진 목록을 다시 보내면 updated_at 그대로 · 수정 활동 행 없음, 순서 · 구성을 바꾸면 바뀌고 활동 행이 남는다")
    void imagesChangeOnlyWhenListDiffers() {
        String first = imageStorage.ownImageUrlPrefix(ME) + "0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f.jpg";
        String second = imageStorage.ownImageUrlPrefix(ME) + "1b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f.png";
        String third = imageStorage.ownImageUrlPrefix(ME) + "2b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f.webp";
        uploadChecker.markUploaded(first);
        uploadChecker.markUploaded(second);
        uploadChecker.markUploaded(third);
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) values (51, 12, ?, ?, 15, null, now(), now(), "
            + "now())", ME, PRODUCT);
        long id = reviewService.write(ME, "닉", "51", 4, "좋은 옷", FitInput.absent(),
            ImageUrlsInput.of(List.of(first, second))).reviewId();
        jdbcTemplate.update("update community.review set updated_at = ? where id = ?", Timestamp.valueOf(BEFORE), id);

        reviewService.update(ME, id, imagesOnly(List.of(first, second)));
        assertThat(updatedAt(id)).isEqualTo(BEFORE);
        assertThat(reviewUpdatedRows(id)).isZero();

        for (List<String> next : List.of(List.of(second, first), List.of(second, first, third))) {
            jdbcTemplate.update("update community.review set updated_at = ? where id = ?", Timestamp.valueOf(BEFORE),
                id);
            long rowsBefore = reviewUpdatedRows(id);

            reviewService.update(ME, id, imagesOnly(next));

            assertThat(updatedAt(id)).isAfter(BEFORE);
            assertThat(reviewUpdatedRows(id)).isEqualTo(rowsBefore + 1);
        }
    }

    private static ReviewChange imagesOnly(List<String> urls) {
        return new ReviewChange(false, null, false, null, FitInput.absent(), ImageUrlsInput.of(urls));
    }

    private long reviewUpdatedRows(long id) {
        return jdbcTemplate.queryForObject("select count(*) from community.activity where type = 'REVIEW_UPDATED' "
            + "and aggregate_id = ?", Long.class, id);
    }

    private long writeAged() {
        return writeAged(31);
    }

    private long writeAged(long orderItemId) {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) values (?, 12, ?, ?, 15, '110', now(), now(), "
            + "now())", orderItemId, ME, PRODUCT);
        long id = reviewService.write(ME, "닉", String.valueOf(orderItemId), 4, "좋은 옷").reviewId();
        jdbcTemplate.update("update community.review set updated_at = ? where id = ?", Timestamp.valueOf(BEFORE), id);
        return id;
    }

    private LocalDateTime updatedAt(long id) {
        return jdbcTemplate.queryForObject("select updated_at from community.review where id = ?", Timestamp.class, id)
            .toLocalDateTime();
    }

    private String status(long id) {
        return jdbcTemplate.queryForObject("select moderation_status from community.review where id = ?", String.class,
            id);
    }
}
