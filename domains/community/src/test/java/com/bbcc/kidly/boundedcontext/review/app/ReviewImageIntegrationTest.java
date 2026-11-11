package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ImageUrlsInput;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.out.storage.FakeReviewImageUploadChecker;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.unit.DataSize;

/**
 * 사진 행의 정합: 작성 · 교체 뒤 순서, 수정 위반 때 그대로, 리뷰 삭제 때 함께 삭제, 설정 기본값.
 */
@IntegrationTest
class ReviewImageIntegrationTest {

    private static final long ME = 13L;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewImageStorage imageStorage;

    @Autowired
    private ReviewImageProperties imageProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeReviewImageUploadChecker uploadChecker;

    @Autowired
    private ReviewModerationAdminService adminService;

    @BeforeEach
    void eligibility() {
        uploadChecker.reset();
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) values (11, 12, ?, 14, 15, null, now(), now(), "
            + "now())", ME);
    }

    @Test
    @DisplayName("작성 순서대로 0부터 저장하고, 교체하면 같은 순서 번호도 새 주소로 바뀐다(유일 제약에 막히지 않음)")
    void replaceKeepsOrder() {
        long id = write(List.of(own(0), own(1), own(2)));

        assertThat(rows(id)).containsExactly(own(0), own(1), own(2));
        reviewService.update(ME, id, imagesChange(List.of(own(2), own(0))));
        assertThat(rows(id)).containsExactly(own(2), own(0));
        assertThat(jdbcTemplate.queryForList("select position from community.review_image where review_id = ? "
            + "order by position", Integer.class, id)).containsExactly(0, 1);
    }

    @Test
    @DisplayName("수정 위반(유효한 사진 + 틀린 별점 · 본문 · 사이즈 없는 핏)은 DB의 사진 행 · 리뷰 값 · 수정 시각을 그대로 둔다")
    void violationKeepsRows() {
        long id = write(List.of(own(0), own(1)));
        Map<String, Object> before = review(id);
        List<String> next = List.of(own(2));

        assertError(() -> reviewService.update(ME, id, new ReviewService.ReviewChange(true, 0, false, null,
            ReviewService.FitInput.absent(), ImageUrlsInput.of(next))), ReviewErrorCode.INVALID_RATING);
        assertError(() -> reviewService.update(ME, id, new ReviewService.ReviewChange(false, null, true, " ",
            ReviewService.FitInput.absent(), ImageUrlsInput.of(next))), ReviewErrorCode.INVALID_CONTENT);
        assertError(() -> reviewService.update(ME, id, new ReviewService.ReviewChange(true, 1, false, null,
            ReviewService.FitInput.text("SMALL"), ImageUrlsInput.of(next))), ReviewErrorCode.FIT_NOT_APPLICABLE);

        assertThat(rows(id)).containsExactly(own(0), own(1));
        assertThat(review(id)).isEqualTo(before);
    }

    @Test
    @DisplayName("끼어들기: 미리 확인 뒤 업로드 확인 중에 리뷰가 지워지면 404이고 사진 행 · 집계가 남지 않는다")
    void deletedDuringCheck() {
        long id = write(List.of(own(0)));
        uploadChecker.duringNextCheck(() -> reviewService.delete(ME, id));

        assertThatThrownBy(() -> reviewService.update(ME, id, imagesChange(List.of(own(0), own(1)))))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.review", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.review_image", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select review_count from community.review_stats where product_id = 14",
            Long.class)).isZero();
    }

    @Test
    @DisplayName("끼어들기: 미리 확인 뒤 업로드 확인 중에 관리자가 숨기면 수정은 반영되지만 숨김 리뷰라 집계는 그대로다")
    void hiddenDuringCheck() {
        long id = write(List.of(own(0)));
        uploadChecker.duringNextCheck(() -> adminService.changeVisibility(99L, id, "HIDDEN"));

        reviewService.update(ME, id, new ReviewService.ReviewChange(true, 1, false, null,
            ReviewService.FitInput.absent(), ImageUrlsInput.of(List.of(own(1), own(0)))));

        assertThat(rows(id)).containsExactly(own(1), own(0));
        assertThat(jdbcTemplate.queryForMap("select moderation_status, rating from community.review where id = ?", id))
            .containsEntry("moderation_status", "CONFIRMED_HIDDEN").containsEntry("rating", 1);
        assertThat(jdbcTemplate.queryForMap("select review_count, rating_sum from community.review_stats "
            + "where product_id = 14")).containsEntry("review_count", 0L).containsEntry("rating_sum", 0L);
    }

    @Test
    @DisplayName("리뷰를 지우면 그 리뷰의 사진 행도 지워진다")
    void deleteRemovesImages() {
        long id = write(List.of(own(0), own(1)));

        reviewService.delete(ME, id);

        assertThat(jdbcTemplate.queryForObject("select count(*) from community.review_image", Long.class)).isZero();
    }

    @Test
    @DisplayName("설정 기본값: 최대 5장 · 10MB, 공개 주소는 우리 버킷 · 리전의 본인 리뷰 경로")
    void defaults() {
        assertThat(imageProperties.maxCount()).isEqualTo(5);
        assertThat(imageProperties.maxFileSize()).isEqualTo(DataSize.ofMegabytes(10));
        assertThat(imageStorage.ownImageUrlPrefix(ME))
            .matches("https://team04-kidly-s3\\.s3\\.ap-northeast-2\\.amazonaws\\.com/[a-z]+/reviews/13/");
    }

    private Map<String, Object> review(long id) {
        return jdbcTemplate.queryForMap("select rating, content, fit, updated_at from community.review where id = ?",
            id);
    }

    private static void assertError(Runnable call, ReviewErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private long write(List<String> urls) {
        return reviewService.write(ME, "닉", "11", 4, "좋아요", ReviewService.FitInput.absent(),
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

    private List<String> rows(long reviewId) {
        return jdbcTemplate.queryForList("select image_url from community.review_image where review_id = ? "
            + "order by position", String.class, reviewId);
    }
}
