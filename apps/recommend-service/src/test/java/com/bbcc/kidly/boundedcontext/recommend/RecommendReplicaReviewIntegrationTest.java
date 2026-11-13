package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaReviewFixture.ORDER_ITEM_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaReviewFixture.PRODUCT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaReviewFixture.REVIEW_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaReviewFixture.REVIEW_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaReviewService;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReview;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReviewRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReviewFit;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 리뷰 복사본 서비스가 실제 DB에서 리뷰 변경됨을 반영하는 규칙과, 사이즈 추천이 리뷰 핏을 읽는 조회를 확인한다.
 * Kafka로 받는 연결은 RecommendCommunityKafkaIntegrationTest가 확인한다.
 */
@IntegrationTest
class RecommendReplicaReviewIntegrationTest {

    @Autowired
    private RecommendReplicaReviewRepository replicaReviewRepository;

    @Autowired
    private RecommendReplicaReviewService replicaReviewService;

    @Test
    @DisplayName("FIT-12 처음 받은 리뷰 변경됨은 주문상품 · 상품 · 핏 · 공개 여부 · 발생 시각을 리뷰 복사본으로 저장한다")
    void saveFirstReviewChanged() {
        replicaReviewService.applyReviewChanged(
            RecommendReplicaReviewFixture.reviewChangedEventAt("SMALL", true, REVIEW_CHANGED_AT));

        assertThat(replicaReviewRepository.findById(REVIEW_ID)).get()
            .extracting(RecommendReplicaReview::getOrderItemId, RecommendReplicaReview::getProductId,
                RecommendReplicaReview::getFit, RecommendReplicaReview::isVisible,
                RecommendReplicaReview::getEventOccurredAt)
            .containsExactly(ORDER_ITEM_ID, PRODUCT_ID, ReviewFit.SMALL, true, REVIEW_CHANGED_AT);
    }
}
