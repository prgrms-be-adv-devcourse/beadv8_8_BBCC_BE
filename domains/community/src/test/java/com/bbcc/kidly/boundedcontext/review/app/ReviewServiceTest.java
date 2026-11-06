package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ReviewView;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewEligibilities;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewStatsCounter;
import com.bbcc.kidly.boundedcontext.review.app.port.Reviews;
import com.bbcc.kidly.boundedcontext.review.domain.Review;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewEligibility;
import com.bbcc.kidly.global.error.BusinessException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewServiceTest {

    private static final long ME = 13L;
    private static final long ORDER_ITEM = 11L;
    private static final long PRODUCT = 14L;
    // 나노초가 있는 고정 기준 시각
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_123);

    private final FakeReviews reviews = new FakeReviews();
    private final FakeStats stats = new FakeStats();
    private final FakeEligibility eligibility = new FakeEligibility();
    private final ReviewService service =
        new ReviewService(reviews, stats, eligibility, new ReviewSeparateTransaction());

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
