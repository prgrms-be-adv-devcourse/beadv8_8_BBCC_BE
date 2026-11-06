package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ImageUrlsInput;
import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ReviewView;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewEligibilities;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImages;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewStatsCounter;
import com.bbcc.kidly.boundedcontext.review.app.port.Reviews;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus;
import com.bbcc.kidly.boundedcontext.review.domain.Review;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewEligibility;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImage;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageUpload;
import com.bbcc.kidly.global.error.BusinessException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

class ReviewServiceTest {

    private static final long ME = 13L;
    private static final long ORDER_ITEM = 11L;
    private static final long PRODUCT = 14L;
    // 나노초가 있는 고정 기준 시각
    private static final String OWN_PREFIX = "https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/test/reviews/13/";
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_123);

    private final FakeReviews reviews = new FakeReviews();
    private final FakeStats stats = new FakeStats();
    private final FakeEligibility eligibility = new FakeEligibility();
    private final FakeReviewImages images = new FakeReviewImages();
    private final OwnPrefixStorage storage = new OwnPrefixStorage();
    private final ReviewService service = service(5);

    @Test
    @DisplayName("내 자격이 있는 주문상품에 쓰면 작성 시점 닉네임으로 저장하고 집계를 +1 · +별점 한다")
    void write() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);

        ReviewView view = service.write(ME, "작성 때 닉네임", String.valueOf(ORDER_ITEM), 4, "좋아요");

        assertThat(view.nickname()).isEqualTo("작성 때 닉네임");
        assertThat(view.productId()).isEqualTo(PRODUCT);
        assertThat(stats.deltas).containsExactly(new Delta(PRODUCT, 1, 4));
    }

    @Test
    @DisplayName("내 자격이 없으면(남의 주문상품 포함) REVIEW_NOT_ELIGIBLE이고 저장하지 않는다")
    void notEligible() {
        eligibility.mine(ORDER_ITEM, ME + 1, PRODUCT);

        assertError(() -> service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요"),
            ReviewErrorCode.REVIEW_NOT_ELIGIBLE);
        assertThat(reviews.saves).isZero();
        assertThat(stats.deltas).isEmpty();
    }

    @Test
    @DisplayName("이미 리뷰가 있으면 저장 전에 확인해 REVIEW_ALREADY_WRITTEN이고 DB 제약까지 가지 않는다")
    void alreadyWritten() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요");

        assertError(() -> service.write(ME, "닉", String.valueOf(ORDER_ITEM), 5, "또"),
            ReviewErrorCode.REVIEW_ALREADY_WRITTEN);
        assertThat(reviews.saves).isEqualTo(1);
        assertThat(stats.deltas).containsExactly(new Delta(PRODUCT, 1, 4));
    }

    @Test
    @DisplayName("확인 뒤 저장이 주문상품 유일 제약에 막히면(동시 작성) REVIEW_ALREADY_WRITTEN이고 집계는 바뀌지 않는다")
    void concurrentDuplicate() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        reviews.violation = violation("23505", ReviewService.ORDER_ITEM_UNIQUE);

        assertError(() -> service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요"),
            ReviewErrorCode.REVIEW_ALREADY_WRITTEN);
        assertThat(stats.deltas).isEmpty();
    }

    @Test
    @DisplayName("다른 제약 위반은 그대로 던진다")
    void otherViolationPropagates() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        reviews.violation = violation("23514", "ck_review_rating");

        assertThatThrownBy(() -> service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "0", "-1", "1.5"})
    @DisplayName("주문상품 ID가 양의 정수 문자열이 아니면 INVALID_ORDER_ITEM_ID")
    void invalidOrderItemId(String orderItemId) {
        assertError(() -> service.write(ME, "닉", orderItemId, 4, "좋아요"), ReviewErrorCode.INVALID_ORDER_ITEM_ID);
    }

    @Test
    @DisplayName("별점 · 본문이 규칙 밖이면 INVALID_RATING · INVALID_CONTENT(자격 확인보다 먼저)")
    void invalidInput() {
        assertError(() -> service.write(ME, "닉", String.valueOf(ORDER_ITEM), 6, "좋아요"),
            ReviewErrorCode.INVALID_RATING);
        assertError(() -> service.write(ME, "닉", String.valueOf(ORDER_ITEM), 5, " "),
            ReviewErrorCode.INVALID_CONTENT);
    }

    @Test
    @DisplayName("수정하면 별점 차이만큼 집계를 바꾸고(본문만 바꾸면 집계 변화 없음), 남이 고치면 REVIEW_FORBIDDEN")
    void update() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        long reviewId = service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요").reviewId();
        stats.deltas.clear();

        ReviewView updated = service.update(ME, reviewId, 2, "별로예요");
        service.update(ME, reviewId, 2, "본문만");

        assertThat(updated.rating()).isEqualTo(2);
        assertThat(stats.deltas).containsExactly(new Delta(PRODUCT, 0, -2));
        assertError(() -> service.update(ME + 1, reviewId, 1, "남"), ReviewErrorCode.REVIEW_FORBIDDEN);
    }

    @Test
    @DisplayName("삭제하면 집계를 −1 · −별점 하고, 남이 지우면 REVIEW_FORBIDDEN, 없으면 REVIEW_NOT_FOUND")
    void delete() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        long reviewId = service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요").reviewId();
        stats.deltas.clear();

        assertError(() -> service.delete(ME + 1, reviewId), ReviewErrorCode.REVIEW_FORBIDDEN);
        service.delete(ME, reviewId);

        assertThat(stats.deltas).containsExactly(new Delta(PRODUCT, -1, -4));
        assertError(() -> service.delete(ME, reviewId), ReviewErrorCode.REVIEW_NOT_FOUND);
        assertError(() -> service.update(ME, reviewId, 3, "다시"), ReviewErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("수정 · 삭제 때 집계 행이 없으면(바뀐 행 0) 조용히 넘기지 않고 예외로 실패한다(메시지에 상품 ID만)")
    void missingStatsRowFails() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        long reviewId = service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요 본문").reviewId();
        stats.adjustedRows = 0;

        assertThatThrownBy(() -> service.update(ME, reviewId, 2, "고친 본문"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("productId=" + PRODUCT)
            .hasMessageNotContaining("본문");
        assertThatThrownBy(() -> service.delete(ME, reviewId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("productId=" + PRODUCT);
    }

    @Test
    @DisplayName("일부 수정: 별점만 보내면 별점 차이만큼 집계, 본문만 보내면 집계 그대로 · 별점 그대로")
    void partialUpdate() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        long reviewId = service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요").reviewId();
        stats.deltas.clear();

        ReviewView ratingOnly = service.update(ME, reviewId,
            new ReviewService.ReviewChange(true, 2, false, null));
        ReviewView contentOnly = service.update(ME, reviewId,
            new ReviewService.ReviewChange(false, null, true, "본문만 고침"));

        assertThat(ratingOnly.rating()).isEqualTo(2);
        assertThat(ratingOnly.content()).isEqualTo("좋아요");
        assertThat(contentOnly.rating()).isEqualTo(2);
        assertThat(contentOnly.content()).isEqualTo("본문만 고침");
        assertThat(stats.deltas).containsExactly(new Delta(PRODUCT, 0, -2));
    }

    @Test
    @DisplayName("바꿀 값이 없으면 NO_FIELDS_TO_UPDATE, 별점 null을 보내면 INVALID_RATING")
    void noFieldsOrNullRating() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        long reviewId = service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요").reviewId();

        assertError(() -> service.update(ME, reviewId, new ReviewService.ReviewChange(false, null, false, null)),
            ReviewErrorCode.NO_FIELDS_TO_UPDATE);
        assertError(() -> service.update(ME, reviewId, new ReviewService.ReviewChange(true, null, false, null)),
            ReviewErrorCode.INVALID_RATING);
    }

    @Test
    @DisplayName("핏 집계: 작성 +1, 수정 이전 −1 · 새 +1, 같은 값 · 별점만 바꾸면 그대로, 지우면 −1, 삭제 −1")
    void fitStats() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT, "110");
        ReviewView written = service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요",
            ReviewService.FitInput.text("SMALL"));
        long id = written.reviewId();

        service.update(ME, id, change(ReviewService.FitInput.text("LARGE")));
        service.update(ME, id, change(ReviewService.FitInput.text("LARGE")));
        service.update(ME, id, new ReviewService.ReviewChange(true, 2, false, null));
        service.update(ME, id, change(ReviewService.FitInput.cleared()));
        service.update(ME, id, change(ReviewService.FitInput.text("TRUE_TO_SIZE")));
        service.delete(ME, id);

        assertThat(written.fit()).isEqualTo("SMALL");
        assertThat(written.purchasedSize()).isEqualTo("110");
        assertThat(stats.fitDeltas).containsExactly(
            List.of(1, 0, 0), List.of(-1, 0, 1), List.of(0, 0, 0), List.of(0, 0, -1), List.of(0, 1, 0),
            List.of(0, -1, 0));
    }

    @Test
    @DisplayName("사이즈 없는 주문상품에 핏을 보내면 FIT_NOT_APPLICABLE, 정해진 값이 아니면 INVALID_FIT(저장 전)")
    void fitErrors() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        assertError(() -> service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요",
            ReviewService.FitInput.text("SMALL")), ReviewErrorCode.FIT_NOT_APPLICABLE);
        assertError(() -> service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요",
            ReviewService.FitInput.text("small")), ReviewErrorCode.INVALID_FIT);
        assertError(() -> service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요",
            ReviewService.FitInput.invalid()), ReviewErrorCode.INVALID_FIT);
        assertThat(reviews.saves).isZero();
        assertThat(stats.deltas).isEmpty();

        long id = service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요").reviewId();
        List<Delta> afterWrite = List.copyOf(stats.deltas);
        List<List<Integer>> fitsAfterWrite = List.copyOf(stats.fitDeltas);
        assertError(() -> service.update(ME, id, change(ReviewService.FitInput.text("LARGE"))),
            ReviewErrorCode.FIT_NOT_APPLICABLE);
        assertThat(stats.deltas).isEqualTo(afterWrite);
        assertThat(stats.fitDeltas).isEqualTo(fitsAfterWrite);
        // 남의 리뷰는 핏 값이 틀려도 작성자 확인이 먼저다(별점 · 본문과 같은 순서)
        assertError(() -> service.update(ME + 1, id, change(ReviewService.FitInput.text("small"))),
            ReviewErrorCode.REVIEW_FORBIDDEN);
        assertError(() -> service.update(ME, id, new ReviewService.ReviewChange(false, null, false, null)),
            ReviewErrorCode.NO_FIELDS_TO_UPDATE);
    }

    @Test
    @DisplayName("사진: 작성하면 본인 주소를 보낸 순서대로 저장하고 응답에 같은 순서로 담는다(없으면 빈 목록)")
    void writeWithImages() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        eligibility.mine(ORDER_ITEM + 1, ME, PRODUCT);
        List<String> urls = List.of(own(2, "png"), own(0, "jpg"), own(1, "webp"));

        ReviewView view = writeWith(ORDER_ITEM, ImageUrlsInput.of(urls));
        ReviewView without = service.write(ME, "닉", String.valueOf(ORDER_ITEM + 1), 4, "좋아요");

        assertThat(view.imageUrls()).containsExactlyElementsOf(urls);
        assertThat(images.urlsOf(view.reviewId())).containsExactlyElementsOf(urls);
        assertThat(without.imageUrls()).isEmpty();
        assertThat(images.urlsOf(without.reviewId())).isEmpty();
    }

    @Test
    @DisplayName("사진 작성 오류: 남의 · 외부 주소 · 틀린 타입은 INVALID_IMAGE_URL, 장수 초과는 TOO_MANY_IMAGES(자격 확인보다 먼저, 아무것도 저장 안 함)")
    void writeImageErrors() {
        assertError(() -> writeWith(ORDER_ITEM, ImageUrlsInput.of(List.of(own(0, "jpg").replace("/13/", "/14/")))),
            ReviewErrorCode.INVALID_IMAGE_URL);
        assertError(() -> writeWith(ORDER_ITEM, ImageUrlsInput.of(List.of("https://evil.example.com/a.jpg"))),
            ReviewErrorCode.INVALID_IMAGE_URL);
        assertError(() -> writeWith(ORDER_ITEM, ImageUrlsInput.invalid()), ReviewErrorCode.INVALID_IMAGE_URL);
        assertThatThrownBy(() -> writeWith(ORDER_ITEM, ImageUrlsInput.of(ownUrls(6))))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ReviewErrorCode.TOO_MANY_IMAGES);
                assertThat(e.getDetail()).contains("5장");
            });
        assertThat(reviews.saves).isZero();
        assertThat(images.rows).isEmpty();
        assertThat(stats.deltas).isEmpty();
    }

    @Test
    @DisplayName("사진 수정: 보내면 전체 교체(새 순서), 키가 없으면 그대로, 빈 목록이면 모두 삭제. 사진만 바꾸면 집계는 그대로")
    void updateImages() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        long id = writeWith(ORDER_ITEM, ImageUrlsInput.of(List.of(own(0, "jpg"), own(1, "jpg")))).reviewId();
        List<Delta> afterWrite = List.copyOf(stats.deltas);

        ReviewView replaced = service.update(ME, id, imagesChange(ImageUrlsInput.of(List.of(own(2, "png"),
            own(0, "jpg")))));
        assertThat(replaced.imageUrls()).containsExactly(own(2, "png"), own(0, "jpg"));
        assertThat(images.urlsOf(id)).containsExactly(own(2, "png"), own(0, "jpg"));

        ReviewView kept = service.update(ME, id, new ReviewService.ReviewChange(false, null, true, "본문만"));
        assertThat(kept.imageUrls()).containsExactly(own(2, "png"), own(0, "jpg"));
        assertThat(images.urlsOf(id)).containsExactly(own(2, "png"), own(0, "jpg"));

        ReviewView cleared = service.update(ME, id, imagesChange(ImageUrlsInput.of(List.of())));
        assertThat(cleared.imageUrls()).isEmpty();
        assertThat(images.urlsOf(id)).isEmpty();
        assertThat(stats.deltas).isEqualTo(afterWrite);
    }

    @Test
    @DisplayName("사진 수정 위반(주소 · 장수 · 핏)은 아무것도 바꾸지 않고, 남의 리뷰는 주소가 틀려도 작성자 확인이 먼저(403)")
    void updateImageViolationChangesNothing() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        long id = writeWith(ORDER_ITEM, ImageUrlsInput.of(List.of(own(0, "jpg")))).reviewId();
        List<Delta> afterWrite = List.copyOf(stats.deltas);

        assertError(() -> service.update(ME, id, new ReviewService.ReviewChange(true, 1, true, "바꿈",
            ReviewService.FitInput.absent(), ImageUrlsInput.of(List.of("https://evil.example.com/a.jpg")))),
            ReviewErrorCode.INVALID_IMAGE_URL);
        assertError(() -> service.update(ME, id, new ReviewService.ReviewChange(true, 1, false, null,
            ReviewService.FitInput.absent(), ImageUrlsInput.of(ownUrls(6)))), ReviewErrorCode.TOO_MANY_IMAGES);
        assertError(() -> service.update(ME, id, new ReviewService.ReviewChange(false, null, false, null,
            ReviewService.FitInput.text("SMALL"), ImageUrlsInput.of(List.of(own(1, "png"))))),
            ReviewErrorCode.FIT_NOT_APPLICABLE);
        assertError(() -> service.update(ME + 1, id, imagesChange(ImageUrlsInput.invalid())),
            ReviewErrorCode.REVIEW_FORBIDDEN);

        Review review = reviews.findForUpdate(id).orElseThrow();
        assertThat(review.getRating()).isEqualTo(4);
        assertThat(review.getContent()).isEqualTo("좋아요");
        assertThat(images.urlsOf(id)).containsExactly(own(0, "jpg"));
        assertThat(stats.deltas).isEqualTo(afterWrite);
    }

    @Test
    @DisplayName("최대 장수 설정을 바꾸면 그 값으로 검사한다(작성 · 수정)")
    void followsConfiguredMaxCount() {
        ReviewService two = service(2);
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);

        assertThatThrownBy(() -> two.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요",
            ReviewService.FitInput.absent(), ImageUrlsInput.of(ownUrls(3))))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getDetail()).contains("2장"));
        long id = two.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요", ReviewService.FitInput.absent(),
            ImageUrlsInput.of(ownUrls(2))).reviewId();
        assertError(() -> two.update(ME, id, imagesChange(ImageUrlsInput.of(ownUrls(3)))),
            ReviewErrorCode.TOO_MANY_IMAGES);
        assertThat(images.urlsOf(id)).hasSize(2);
    }

    @Test
    @DisplayName("검사: 새 리뷰는 PENDING. 글 · 사진이 바뀐 수정은 PENDING + 버전 +1, 별점 · 핏만 · 같은 글 · 같은 사진이면 그대로")
    void contentEditRequeuesModeration() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        long id = writeWith(ORDER_ITEM, ImageUrlsInput.of(List.of(own(0, "jpg")))).reviewId();
        Review review = reviews.findForUpdate(id).orElseThrow();
        assertThat(review.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        ReflectionTestUtils.setField(review, "moderationStatus", ModerationStatus.PASSED);

        service.update(ME, id, new ReviewService.ReviewChange(true, 2, false, null));
        service.update(ME, id, new ReviewService.ReviewChange(false, null, true, " 좋아요 "));
        service.update(ME, id, imagesChange(ImageUrlsInput.of(List.of(own(0, "jpg")))));
        assertThat(review.getModerationStatus()).isEqualTo(ModerationStatus.PASSED);
        assertThat(review.getContentVersion()).isZero();

        service.update(ME, id, new ReviewService.ReviewChange(false, null, true, "바뀐 글"));
        assertThat(review.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(review.getContentVersion()).isEqualTo(1L);

        ReflectionTestUtils.setField(review, "moderationStatus", ModerationStatus.RESTORED);
        service.update(ME, id, imagesChange(ImageUrlsInput.of(List.of(own(1, "png")))));
        assertThat(review.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(review.getContentVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("검사: 관리자 숨김 확정 리뷰는 글을 고쳐도 숨김 그대로")
    void confirmedHiddenStaysHidden() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        long id = service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요").reviewId();
        Review review = reviews.findForUpdate(id).orElseThrow();
        ReflectionTestUtils.setField(review, "moderationStatus", ModerationStatus.CONFIRMED_HIDDEN);

        service.update(ME, id, new ReviewService.ReviewChange(false, null, true, "고친 글"));

        assertThat(review.getModerationStatus()).isEqualTo(ModerationStatus.CONFIRMED_HIDDEN);
    }

    @ParameterizedTest(name = "[{0}]")
    @EnumSource(value = ModerationStatus.class, names = {"HIDDEN", "CONFIRMED_HIDDEN"})
    @DisplayName("집계: 숨김 리뷰는 별점 · 핏을 고치거나 지워도 집계를 건드리지 않는다(이미 빠져 있다)")
    void hiddenReviewLeavesStats(ModerationStatus status) {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT, "110");
        long id = service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요",
            ReviewService.FitInput.text("SMALL")).reviewId();
        ReflectionTestUtils.setField(reviews.findForUpdate(id).orElseThrow(), "moderationStatus", status);
        List<Delta> afterWrite = List.copyOf(stats.deltas);

        service.update(ME, id, new ReviewService.ReviewChange(true, 1, false, null,
            ReviewService.FitInput.text("LARGE")));
        service.delete(ME, id);

        assertThat(stats.deltas).isEqualTo(afterWrite);
    }

    @Test
    @DisplayName("업로드 확인(작성): 하나라도 올리지 않았으면 IMAGE_NOT_UPLOADED, 확인 불가면 IMAGE_STORAGE_UNAVAILABLE, 둘 다 저장 안 함")
    void writeChecksUpload() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        storage.missing.add(own(1, "png"));

        assertError(() -> writeWith(ORDER_ITEM, ImageUrlsInput.of(List.of(own(0, "jpg"), own(1, "png")))),
            ReviewErrorCode.IMAGE_NOT_UPLOADED);
        assertThat(storage.checks).containsExactly(List.of(own(0, "jpg"), own(1, "png")));
        storage.unavailable = true;
        assertError(() -> writeWith(ORDER_ITEM, ImageUrlsInput.of(List.of(own(0, "jpg")))),
            ReviewErrorCode.IMAGE_STORAGE_UNAVAILABLE);

        assertThat(reviews.saves).isZero();
        assertThat(images.rows).isEmpty();
        assertThat(stats.deltas).isEmpty();
    }

    @Test
    @DisplayName("업로드 확인(작성): 사진이 없으면 부르지 않고, 형식 · 장수 · 자격 오류가 먼저다(확인 호출 없음)")
    void writeChecksAfterOtherRules() {
        storage.missing.add(own(0, "jpg"));

        assertError(() -> writeWith(ORDER_ITEM, ImageUrlsInput.of(List.of(own(0, "jpg")))),
            ReviewErrorCode.REVIEW_NOT_ELIGIBLE);
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        assertError(() -> writeWith(ORDER_ITEM, ImageUrlsInput.of(List.of("https://evil.example.com/a.jpg"))),
            ReviewErrorCode.INVALID_IMAGE_URL);
        assertError(() -> writeWith(ORDER_ITEM, ImageUrlsInput.of(ownUrls(6))), ReviewErrorCode.TOO_MANY_IMAGES);
        service.write(ME, "닉", String.valueOf(ORDER_ITEM), 4, "좋아요");

        assertThat(storage.checks).isEmpty();
    }

    @Test
    @DisplayName("업로드 확인(수정): 새로 붙는 주소만 확인하고, 사진 키가 없거나 빈 목록 · 이미 붙은 주소만이면 부르지 않는다")
    void updateChecksOnlyNewUrls() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        long id = writeWith(ORDER_ITEM, ImageUrlsInput.of(List.of(own(0, "jpg")))).reviewId();
        storage.checks.clear();

        service.update(ME, id, imagesChange(ImageUrlsInput.of(List.of(own(0, "jpg"), own(1, "png")))));
        assertThat(storage.checks).containsExactly(List.of(own(1, "png")));
        storage.checks.clear();

        service.update(ME, id, new ReviewService.ReviewChange(true, 2, false, null));
        service.update(ME, id, imagesChange(ImageUrlsInput.of(List.of(own(1, "png"), own(0, "jpg")))));
        service.update(ME, id, imagesChange(ImageUrlsInput.of(List.of())));
        assertThat(storage.checks).isEmpty();
    }

    @Test
    @DisplayName("업로드 확인(수정): 새 주소가 없거나 확인 불가면 아무것도 바뀌지 않고, 작성자 · 값 오류가 확인보다 먼저다")
    void updateUploadFailureChangesNothing() {
        eligibility.mine(ORDER_ITEM, ME, PRODUCT);
        long id = writeWith(ORDER_ITEM, ImageUrlsInput.of(List.of(own(0, "jpg")))).reviewId();
        List<Delta> afterWrite = List.copyOf(stats.deltas);
        storage.checks.clear();
        storage.missing.add(own(2, "png"));

        assertError(() -> service.update(ME, id, new ReviewService.ReviewChange(true, 1, true, "바꿈",
            ReviewService.FitInput.absent(), ImageUrlsInput.of(List.of(own(2, "png"))))),
            ReviewErrorCode.IMAGE_NOT_UPLOADED);
        storage.unavailable = true;
        assertError(() -> service.update(ME, id, imagesChange(ImageUrlsInput.of(List.of(own(3, "png"))))),
            ReviewErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        int checksBefore = storage.checks.size();
        assertError(() -> service.update(ME + 1, id, imagesChange(ImageUrlsInput.of(List.of(own(3, "png"))))),
            ReviewErrorCode.REVIEW_FORBIDDEN);
        assertError(() -> service.update(ME, id, new ReviewService.ReviewChange(true, 9, false, null,
            ReviewService.FitInput.absent(), ImageUrlsInput.of(List.of(own(3, "png"))))),
            ReviewErrorCode.INVALID_RATING);
        assertError(() -> service.update(ME, 999L, imagesChange(ImageUrlsInput.of(List.of(own(3, "png"))))),
            ReviewErrorCode.REVIEW_NOT_FOUND);

        assertThat(storage.checks).hasSize(checksBefore);
        Review review = reviews.findForUpdate(id).orElseThrow();
        assertThat(review.getRating()).isEqualTo(4);
        assertThat(review.getContent()).isEqualTo("좋아요");
        assertThat(images.urlsOf(id)).containsExactly(own(0, "jpg"));
        assertThat(stats.deltas).isEqualTo(afterWrite);
    }

    private ReviewService service(int maxImages) {
        return new ReviewService(reviews, stats, eligibility, new ReviewSeparateTransaction(), images,
            storage, new ReviewImageProperties(maxImages, DataSize.ofMegabytes(10)),
            org.mockito.Mockito.mock(ReviewImageCleanupQueue.class));
    }

    private ReviewView writeWith(long orderItemId, ImageUrlsInput imageUrls) {
        return service.write(ME, "닉", String.valueOf(orderItemId), 4, "좋아요", ReviewService.FitInput.absent(),
            imageUrls);
    }

    private static ReviewService.ReviewChange imagesChange(ImageUrlsInput imageUrls) {
        return new ReviewService.ReviewChange(false, null, false, null, ReviewService.FitInput.absent(), imageUrls);
    }

    private static String own(int index, String extension) {
        return OWN_PREFIX + String.format("0b6f3c1e-8a2d-4c5b-9e7f-%012d", index) + "." + extension;
    }

    private static List<String> ownUrls(int count) {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            urls.add(own(i, "png"));
        }
        return urls;
    }

    private static ReviewService.ReviewChange change(ReviewService.FitInput fit) {
        return new ReviewService.ReviewChange(false, null, false, null, fit);
    }

    private static void assertError(Runnable call, ReviewErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private static DataIntegrityViolationException violation(String sqlState, String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
            new ConstraintViolationException("could not execute statement", new SQLException("violation", sqlState),
                constraint));
    }

    private record Delta(Long productId, int count, int rating) {
    }

    private static final class OwnPrefixStorage implements ReviewImageStorage {

        // 업로드 확인에 넘어온 주소(호출마다 한 묶음)와, 없음 · 확인 불가로 볼 주소
        private final List<List<String>> checks = new ArrayList<>();
        private final java.util.Set<String> missing = new java.util.HashSet<>();
        private boolean unavailable;

        @Override
        public UploadUrl issueUploadUrl(ReviewImageUpload upload, long memberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Set<String> findNotUploaded(List<String> imageUrls) {
            checks.add(List.copyOf(imageUrls));
            if (unavailable) {
                throw new CheckUnavailableException();
            }
            return imageUrls.stream().filter(missing::contains)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        }

        @Override
        public String ownImageUrlPrefix(long memberId) {
            return "https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/test/reviews/" + memberId + "/";
        }
    }

    private static final class FakeReviewImages implements ReviewImages {

        private final List<ReviewImage> rows = new ArrayList<>();

        List<String> urlsOf(Long reviewId) {
            return findByReviewIdOrderByPositionAsc(reviewId).stream().map(ReviewImage::getImageUrl).toList();
        }

        @Override
        public <S extends ReviewImage> List<S> saveAll(Iterable<S> images) {
            List<S> saved = new ArrayList<>();
            images.forEach(saved::add);
            rows.addAll(saved);
            return saved;
        }

        @Override
        public List<ReviewImage> findByReviewIdOrderByPositionAsc(Long reviewId) {
            return rows.stream().filter(image -> image.getReviewId().equals(reviewId))
                .sorted(Comparator.comparingInt(ReviewImage::getPosition)).toList();
        }

        @Override
        public List<ReviewImage> findByReviewIdInOrderByReviewIdAscPositionAsc(Collection<Long> reviewIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteAllOfReview(Long reviewId) {
            int before = rows.size();
            rows.removeIf(image -> image.getReviewId().equals(reviewId));
            return before - rows.size();
        }

        @Override
        public boolean existsByImageUrl(String imageUrl) {
            return rows.stream().anyMatch(image -> image.getImageUrl().equals(imageUrl));
        }

        @Override
        public List<String> findAttachedUrls(Collection<String> imageUrls) {
            return rows.stream().map(ReviewImage::getImageUrl).filter(imageUrls::contains).distinct().toList();
        }
    }

    private static final class FakeStats implements ReviewStatsCounter {

        private final List<Delta> deltas = new ArrayList<>();
        // adjust가 돌려줄 바뀐 행 수. 0이면 집계 행이 없는 상황이다
        private int adjustedRows = 1;

        // 핏 집계 증감(작다 · 맞다 · 크다). 핏이 바뀌지 않으면 0 · 0 · 0
        private final List<List<Integer>> fitDeltas = new ArrayList<>();

        @Override
        public void addReview(Long productId, int rating, int fitSmall, int fitTrueToSize, int fitLarge,
            LocalDateTime now) {
            deltas.add(new Delta(productId, 1, rating));
            fitDeltas.add(List.of(fitSmall, fitTrueToSize, fitLarge));
        }

        @Override
        public int adjust(Long productId, int countDelta, int ratingDelta, int fitSmall, int fitTrueToSize,
            int fitLarge, LocalDateTime now) {
            deltas.add(new Delta(productId, countDelta, ratingDelta));
            fitDeltas.add(List.of(fitSmall, fitTrueToSize, fitLarge));
            return adjustedRows;
        }
    }

    private static final class FakeEligibility implements ReviewEligibilities {

        private final Map<Long, ReviewEligibility> rows = new HashMap<>();

        void mine(long orderItemId, long memberId, long productId) {
            mine(orderItemId, memberId, productId, null);
        }

        void mine(long orderItemId, long memberId, long productId, String size) {
            rows.put(orderItemId, ReviewEligibility.create(new ReviewEligibility.Values(orderItemId, 12L, memberId,
                productId, 15L, size, FIXED_TIME)));
        }

        @Override
        public Optional<ReviewEligibility> findByOrderItemIdAndMemberId(Long orderItemId, Long memberId) {
            return Optional.ofNullable(rows.get(orderItemId))
                .filter(eligibility -> eligibility.getMemberId().equals(memberId));
        }

        @Override
        public boolean existsByOrderItemId(Long orderItemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReviewEligibility saveAndFlush(ReviewEligibility eligibility) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EligibleRow> findEligible(Long memberId, Limit limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EligibleRow> findEligibleAfter(Long memberId, LocalDateTime confirmedAt, Long id, Limit limit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeReviews implements Reviews {

        private final Map<Long, Review> rows = new HashMap<>();
        private long nextId = 1;
        private int saves;
        private DataIntegrityViolationException violation;

        @Override
        public boolean existsByOrderItemId(Long orderItemId) {
            return rows.values().stream().anyMatch(review -> review.getOrderItemId().equals(orderItemId));
        }

        @Override
        public Review saveAndFlush(Review review) {
            if (violation != null) {
                throw violation;
            }
            saves++;
            if (review.getId() == null) {
                ReflectionTestUtils.setField(review, "id", nextId++);
                ReflectionTestUtils.setField(review, "createdAt", FIXED_TIME);
            }
            ReflectionTestUtils.setField(review, "updatedAt", FIXED_TIME);
            rows.put(review.getId(), review);
            return review;
        }

        @Override
        public Optional<Review> findForUpdate(Long reviewId) {
            return Optional.ofNullable(rows.get(reviewId));
        }

        @Override
        public Optional<Review> findById(Long reviewId) {
            return Optional.ofNullable(rows.get(reviewId));
        }

        @Override
        public void delete(Review review) {
            rows.remove(review.getId());
        }

        @Override
        public List<ReviewListRow> findByProduct(Long productId, Limit limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReviewListRow> findByProductAfter(Long productId, LocalDateTime createdAt, Long id,
            Limit limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReviewListRow> findByMember(Long memberId, Limit limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReviewListRow> findByMemberAfter(Long memberId, LocalDateTime createdAt, Long id, Limit limit) {
            throw new UnsupportedOperationException();
        }
    }
}
