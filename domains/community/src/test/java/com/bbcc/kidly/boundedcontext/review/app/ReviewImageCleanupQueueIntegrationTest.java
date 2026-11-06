package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ImageUrlsInput;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.out.storage.FakeReviewImageUploadChecker;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 정리 대기 기록: 리뷰 수정으로 빠진 사진 · 리뷰 삭제로 남은 사진만 그 트랜잭션에서 대기에 들어가고(그대로 둔 · 새 사진은 아님),
 * 다시 붙이면 같은 트랜잭션에서 대기가 지워지며, 같은 key는 한 행이다. 대기는 pending-delay 뒤가 처리 차례다.
 */
@IntegrationTest
class ReviewImageCleanupQueueIntegrationTest {

    private static final long ME = 13L;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewImageStorage imageStorage;

    @Autowired
    private ReviewImageCleanupStorage cleanupStorage;

    @Autowired
    private ReviewImageCleanupProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeReviewImageUploadChecker uploadChecker;

    @BeforeEach
    void eligibility() {
        uploadChecker.reset();
        for (long orderItem : List.of(11L, 21L)) {
            jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, "
                + "product_id, option_id, size, confirmed_at, created_at, updated_at) values (?, 12, ?, 14, 15, null, "
                + "now(), now(), now())", orderItem, ME);
        }
    }

    @Test
    @DisplayName("수정으로 빠진 사진만 REPLACED로 대기에 들어간다(그대로 둔 사진 · 새 사진은 아님). 처리 차례는 pending-delay 뒤")
    void replacedImagesQueued() {
        long id = write("11", List.of(own(0), own(1), own(2)));
        LocalDateTime before = LocalDateTime.now();

        reviewService.update(ME, id, imagesChange(List.of(own(1), own(3))));

        assertThat(queuedKeys()).containsExactlyInAnyOrder(key(0), key(2));
        Map<String, Object> row = jdbcTemplate.queryForMap("select reason, attempts, created_at, next_attempt_at "
            + "from community.review_image_cleanup where object_key = ?", key(0));
        assertThat(row).containsEntry("reason", "REPLACED").containsEntry("attempts", 0);
        LocalDateTime createdAt = ((java.sql.Timestamp) row.get("created_at")).toLocalDateTime();
        LocalDateTime nextAttemptAt = ((java.sql.Timestamp) row.get("next_attempt_at")).toLocalDateTime();
        assertThat(createdAt).isCloseTo(before, within(5, ChronoUnit.SECONDS));
        assertThat(Duration.between(createdAt, nextAttemptAt)).isEqualTo(properties.pendingDelay());
    }

    @Test
    @DisplayName("사진을 보내지 않은 수정 · 같은 목록 수정 · 순서만 바꾼 수정은 대기에 넣지 않는다")
    void unchangedImagesNotQueued() {
        long id = write("11", List.of(own(0), own(1)));

        reviewService.update(ME, id, new ReviewService.ReviewChange(true, 2, false, null));
        reviewService.update(ME, id, imagesChange(List.of(own(0), own(1))));
        reviewService.update(ME, id, imagesChange(List.of(own(1), own(0))));

        assertThat(queuedKeys()).isEmpty();
    }

    @Test
    @DisplayName("리뷰를 지우면 붙어 있던 사진이 모두 REVIEW_DELETED로 대기에 들어간다(사진 행이 지워지기 전에 읽는다)")
    void deletedReviewImagesQueued() {
        long id = write("11", List.of(own(0), own(1)));

        reviewService.delete(ME, id);

        assertThat(queuedKeys()).containsExactlyInAnyOrder(key(0), key(1));
        assertThat(jdbcTemplate.queryForList("select distinct reason from community.review_image_cleanup",
            String.class)).containsExactly("REVIEW_DELETED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.review_image", Long.class)).isZero();
    }

    @Test
    @DisplayName("빠졌던 사진을 다시 붙이면(수정 · 작성) 같은 트랜잭션에서 대기가 지워진다")
    void reattachCancels() {
        long first = write("11", List.of(own(0), own(1)));
        reviewService.update(ME, first, imagesChange(List.of()));
        assertThat(queuedKeys()).containsExactlyInAnyOrder(key(0), key(1));

        reviewService.update(ME, first, imagesChange(List.of(own(0))));
        assertThat(queuedKeys()).containsExactly(key(1));

        write("21", List.of(own(1)));
        assertThat(queuedKeys()).isEmpty();
    }

    @Test
    @DisplayName("같은 key는 두 번 들어와도 한 행이다(이미 대기면 그대로, 수정 · 삭제는 실패하지 않는다)")
    void sameKeyOnce() {
        long first = write("11", List.of(own(0)));
        long second = write("21", List.of(own(0)));

        reviewService.delete(ME, first);
        reviewService.update(ME, second, imagesChange(List.of()));

        assertThat(queuedKeys()).containsExactly(key(0));
        assertThat(jdbcTemplate.queryForObject("select reason from community.review_image_cleanup", String.class))
            .isEqualTo("REVIEW_DELETED");
    }

    @Test
    @DisplayName("사진 없는 리뷰를 지우면 대기에 아무것도 넣지 않는다")
    void noImagesNothingQueued() {
        long id = write("11", List.of());

        reviewService.delete(ME, id);

        assertThat(queuedKeys()).isEmpty();
    }

    private long write(String orderItemId, List<String> urls) {
        return reviewService.write(ME, "닉", orderItemId, 4, "좋아요", ReviewService.FitInput.absent(),
            ImageUrlsInput.of(urls)).reviewId();
    }

    private static ReviewService.ReviewChange imagesChange(List<String> urls) {
        return new ReviewService.ReviewChange(false, null, false, null, ReviewService.FitInput.absent(),
            ImageUrlsInput.of(urls));
    }

    // 올린 것으로 친 본인 주소(가짜 업로드 확인기에 등록)
    private String own(int index) {
        String url = imageStorage.ownImageUrlPrefix(ME) + String.format("0b6f3c1e-8a2d-4c5b-9e7f-%012d", index)
            + ".jpg";
        uploadChecker.markUploaded(url);
        return url;
    }

    private String key(int index) {
        return cleanupStorage.keyOf(own(index)).orElseThrow();
    }

    private List<String> queuedKeys() {
        return jdbcTemplate.queryForList("select object_key from community.review_image_cleanup", String.class);
    }
}
