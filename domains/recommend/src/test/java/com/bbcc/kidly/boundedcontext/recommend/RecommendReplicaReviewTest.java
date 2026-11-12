package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReview;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReviewFit;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 리뷰 변경됨 이벤트에서 옮겨 둔 리뷰 복사본의 규칙을 확인한다.
 */
class RecommendReplicaReviewTest {

    private static final Long REVIEW_ID = 501L;
    private static final Long ORDER_ITEM_ID = 601L;
    private static final Long PRODUCT_ID = 31L;
    private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 11, 5, 9, 0);

    @Test
    @DisplayName("FIT-10 공개된 리뷰의 변경 값으로 복사본을 만들면 주문상품 · 상품과 그 핏을 쓴다")
    void createVisibleReview() {
        RecommendReplicaReview review = RecommendReplicaReview.create(
            new RecommendReplicaReview.ChangeInfo(REVIEW_ID, ORDER_ITEM_ID, PRODUCT_ID, ReviewFit.SMALL, true,
                REVIEWED_AT));

        assertThat(review)
            .extracting(RecommendReplicaReview::getReviewId, RecommendReplicaReview::getOrderItemId,
                RecommendReplicaReview::getProductId, RecommendReplicaReview::countedFit,
                RecommendReplicaReview::getEventOccurredAt)
            .containsExactly(REVIEW_ID, ORDER_ITEM_ID, PRODUCT_ID, ReviewFit.SMALL, REVIEWED_AT);
    }

    @Test
    @DisplayName("FIT-10 더 늦은 숨김 이벤트를 받으면 핏을 쓰지 않고, 그 뒤 늦게 도착한 예전 공개 이벤트는 핏을 되살리지 않는다")
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
            .extracting(RecommendReplicaReview::countedFit, RecommendReplicaReview::getEventOccurredAt)
            .containsExactly(null, hiddenAt);
    }
}
