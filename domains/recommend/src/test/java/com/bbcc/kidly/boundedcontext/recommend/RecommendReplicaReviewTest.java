package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReview;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReviewFit;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 리뷰 변경됨 이벤트에서 옮겨 둔 리뷰 복사본의 규칙을 확인한다.
 */
class RecommendReplicaReviewTest {

    private static final Long REVIEW_ID = 501L;
    private static final Long ORDER_ITEM_ID = 601L;
    private static final Long PRODUCT_ID = 31L;
    private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 11, 5, 9, 0);

    @Test
    @DisplayName("FIT-12 공개된 리뷰의 변경 값으로 복사본을 만들면 주문상품 · 상품과 그 핏을 쓴다")
    void createVisibleReview() {
        RecommendReplicaReview review = RecommendReplicaReview.create(
            new RecommendReplicaReview.ChangeInfo(REVIEW_ID, ORDER_ITEM_ID, PRODUCT_ID, ReviewFit.SMALL, true,
                REVIEWED_AT));

        assertThat(review)
            .extracting(RecommendReplicaReview::getReviewId, RecommendReplicaReview::getOrderItemId,
                RecommendReplicaReview::getProductId, RecommendReplicaReview::visibleFit,
                RecommendReplicaReview::getEventOccurredAt)
            .containsExactly(REVIEW_ID, ORDER_ITEM_ID, PRODUCT_ID, ReviewFit.SMALL, REVIEWED_AT);
    }

    @Test
    @DisplayName("FIT-12 더 늦은 숨김 이벤트를 받으면 핏을 쓰지 않고, 그 뒤 늦게 도착한 예전 공개 이벤트는 핏을 되살리지 않는다")
    void hiddenReviewIsNotRevivedByLateEvent() {
        RecommendReplicaReview review = RecommendReplicaReview.create(
            new RecommendReplicaReview.ChangeInfo(REVIEW_ID, ORDER_ITEM_ID, PRODUCT_ID, ReviewFit.SMALL, true,
                REVIEWED_AT));

        LocalDateTime hiddenAt = REVIEWED_AT.plusDays(1);
        review.applyIfNewer(new RecommendReplicaReview.ChangeInfo(REVIEW_ID, ORDER_ITEM_ID, PRODUCT_ID,
            ReviewFit.SMALL, false, hiddenAt));
        review.applyIfNewer(new RecommendReplicaReview.ChangeInfo(REVIEW_ID, ORDER_ITEM_ID, PRODUCT_ID,
            ReviewFit.LARGE, true, REVIEWED_AT.plusHours(1)));

        assertThat(review)
            .extracting(RecommendReplicaReview::visibleFit, RecommendReplicaReview::getEventOccurredAt)
            .containsExactly(null, hiddenAt);
    }

    @Test
    @DisplayName("FIT-12 한 주문상품에 공개된 핏 리뷰가 둘이면 더 늦게 일어난 리뷰의 핏을 그 주문상품의 핏으로 쓴다")
    void pickLatestFitPerOrderItem() {
        RecommendReplicaReview earlier = review(1L, ORDER_ITEM_ID, ReviewFit.SMALL, true, REVIEWED_AT);
        RecommendReplicaReview later = review(2L, ORDER_ITEM_ID, ReviewFit.LARGE, true, REVIEWED_AT.plusDays(1));
        RecommendReplicaReview other = review(3L, ORDER_ITEM_ID + 1, ReviewFit.TRUE_TO_SIZE, true, REVIEWED_AT);

        assertThat(RecommendReplicaReview.visibleFitsByOrderItem(List.of(later, earlier, other)))
            .containsOnly(entry(ORDER_ITEM_ID, ReviewFit.LARGE), entry(ORDER_ITEM_ID + 1, ReviewFit.TRUE_TO_SIZE));
        assertThat(RecommendReplicaReview.visibleFitsByOrderItem(List.of(earlier, other, later)))
            .containsEntry(ORDER_ITEM_ID, ReviewFit.LARGE);
    }

    @Test
    @DisplayName("FIT-12 한 주문상품의 두 리뷰가 같은 시각에 일어났으면 읽은 순서와 상관없이 리뷰 ID가 큰 리뷰의 핏을 쓴다")
    void pickFitOfLargerReviewIdAtSameTime() {
        RecommendReplicaReview first = review(1L, ORDER_ITEM_ID, ReviewFit.SMALL, true, REVIEWED_AT);
        RecommendReplicaReview second = review(2L, ORDER_ITEM_ID, ReviewFit.LARGE, true, REVIEWED_AT);

        assertThat(RecommendReplicaReview.visibleFitsByOrderItem(List.of(first, second)))
            .containsOnly(entry(ORDER_ITEM_ID, ReviewFit.LARGE));
        assertThat(RecommendReplicaReview.visibleFitsByOrderItem(List.of(second, first)))
            .containsOnly(entry(ORDER_ITEM_ID, ReviewFit.LARGE));
    }

    @Test
    @DisplayName("FIT-12 숨긴 리뷰와 핏을 남기지 않은 리뷰는 주문상품의 핏으로 쓰지 않는다")
    void skipHiddenOrFitlessReviewPerOrderItem() {
        RecommendReplicaReview hidden = review(1L, ORDER_ITEM_ID, ReviewFit.SMALL, false, REVIEWED_AT);
        RecommendReplicaReview withoutFit = review(2L, ORDER_ITEM_ID + 1, null, true, REVIEWED_AT);

        assertThat(RecommendReplicaReview.visibleFitsByOrderItem(List.of(hidden, withoutFit))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("firstChangesWithNull")
    @DisplayName("리뷰 변경 값에 필수 값이 없으면 복사본을 만들지 않는다")
    void rejectFirstChangeWithNull(RecommendReplicaReview.ChangeInfo info) {
        assertThatThrownBy(() -> RecommendReplicaReview.create(info)).isInstanceOf(NullPointerException.class);
    }

    static Stream<Named<RecommendReplicaReview.ChangeInfo>> firstChangesWithNull() {
        return changesWithNullAt(REVIEWED_AT);
    }

    @ParameterizedTest
    @MethodSource("laterChangesWithNull")
    @DisplayName("리뷰 변경 값에 필수 값이 없으면 복사본을 덮어쓰지 않고 이전 값을 그대로 둔다")
    void keepPreviousValuesWhenLaterChangeHasNull(RecommendReplicaReview.ChangeInfo info) {
        RecommendReplicaReview review = review(REVIEW_ID, ORDER_ITEM_ID, ReviewFit.SMALL, true, REVIEWED_AT);

        assertThatThrownBy(() -> review.applyIfNewer(info)).isInstanceOf(NullPointerException.class);

        assertThat(review)
            .extracting(RecommendReplicaReview::getFit, RecommendReplicaReview::isVisible,
                RecommendReplicaReview::getEventOccurredAt)
            .containsExactly(ReviewFit.SMALL, true, REVIEWED_AT);
    }

    static Stream<Named<RecommendReplicaReview.ChangeInfo>> laterChangesWithNull() {
        return Stream.concat(changesWithNullAt(REVIEWED_AT.plusMinutes(1)), Stream.of(
            Named.of("occurredAt", new RecommendReplicaReview.ChangeInfo(REVIEW_ID, ORDER_ITEM_ID, PRODUCT_ID,
                ReviewFit.LARGE, false, null))));
    }

    @ParameterizedTest(name = "저장된 시각에서 {0}분")
    @ValueSource(longs = {0, -1})
    @DisplayName("FIT-12 같은 시각이거나 더 이르게 일어난 리뷰 변경은 필수 값이 없어도 예외 없이 무시한다")
    void ignoreNotLaterChangeEvenWithNull(long minutes) {
        RecommendReplicaReview review = review(REVIEW_ID, ORDER_ITEM_ID, ReviewFit.SMALL, true, REVIEWED_AT);

        review.applyIfNewer(new RecommendReplicaReview.ChangeInfo(REVIEW_ID, null, null, ReviewFit.LARGE, null,
            REVIEWED_AT.plusMinutes(minutes)));

        assertThat(review)
            .extracting(RecommendReplicaReview::getFit, RecommendReplicaReview::isVisible,
                RecommendReplicaReview::getEventOccurredAt)
            .containsExactly(ReviewFit.SMALL, true, REVIEWED_AT);
    }

    // 핏은 비어 있어도 되므로 넣지 않는다
    private static Stream<Named<RecommendReplicaReview.ChangeInfo>> changesWithNullAt(LocalDateTime occurredAt) {
        return Stream.of(
            Named.of("reviewId", new RecommendReplicaReview.ChangeInfo(null, ORDER_ITEM_ID, PRODUCT_ID,
                ReviewFit.LARGE, false, occurredAt)),
            Named.of("orderItemId", new RecommendReplicaReview.ChangeInfo(REVIEW_ID, null, PRODUCT_ID,
                ReviewFit.LARGE, false, occurredAt)),
            Named.of("productId", new RecommendReplicaReview.ChangeInfo(REVIEW_ID, ORDER_ITEM_ID, null,
                ReviewFit.LARGE, false, occurredAt)),
            Named.of("visible", new RecommendReplicaReview.ChangeInfo(REVIEW_ID, ORDER_ITEM_ID, PRODUCT_ID,
                ReviewFit.LARGE, null, occurredAt)));
    }

    private static RecommendReplicaReview review(Long reviewId, Long orderItemId, ReviewFit fit, boolean visible,
        LocalDateTime occurredAt) {
        return RecommendReplicaReview.create(
            new RecommendReplicaReview.ChangeInfo(reviewId, orderItemId, PRODUCT_ID, fit, visible, occurredAt));
    }
}
