package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaReviewFixture.ORDER_ITEM_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaReviewFixture.PRODUCT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaReviewFixture.REVIEW_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaReviewFixture.REVIEW_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaReviewService;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReview;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReviewRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReviewFit;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Test
    @DisplayName("FIT-12 이미 있는 리뷰 복사본은 더 늦게 일어난 이벤트의 핏 · 공개 여부로 덮어써진다")
    void overwriteWithLaterEvent() {
        LocalDateTime later = REVIEW_CHANGED_AT.plusMinutes(1);
        replicaReviewService.applyReviewChanged(
            RecommendReplicaReviewFixture.reviewChangedEventAt("SMALL", true, REVIEW_CHANGED_AT));

        replicaReviewService.applyReviewChanged(
            RecommendReplicaReviewFixture.reviewChangedEventAt("LARGE", false, later));

        assertThat(replicaReviewRepository.findById(REVIEW_ID)).get()
            .extracting(RecommendReplicaReview::getFit, RecommendReplicaReview::isVisible,
                RecommendReplicaReview::getEventOccurredAt)
            .containsExactly(ReviewFit.LARGE, false, later);
    }

    // 같은 이벤트를 두 번 받는 경우도 발생 시각이 같으므로 0분 경우가 확인한다. 값을 바꿔 보내야 무시됐는지 알 수 있다
    @ParameterizedTest(name = "저장된 시각에서 {0}분")
    @ValueSource(longs = {0, -1})
    @DisplayName("FIT-12 저장된 시각보다 늦지 않은 리뷰 변경됨은 무시해, 숨긴 리뷰를 늦게 도착한 예전 공개 이벤트가 되살리지 않는다")
    void ignoreNotLaterEvent(long minutes) {
        LocalDateTime hiddenAt = REVIEW_CHANGED_AT.plusMinutes(5);
        replicaReviewService.applyReviewChanged(
            RecommendReplicaReviewFixture.reviewChangedEventAt("SMALL", false, hiddenAt));

        replicaReviewService.applyReviewChanged(
            RecommendReplicaReviewFixture.reviewChangedEventAt("LARGE", true, hiddenAt.plusMinutes(minutes)));

        assertThat(replicaReviewRepository.findById(REVIEW_ID)).get()
            .extracting(RecommendReplicaReview::getFit, RecommendReplicaReview::isVisible,
                RecommendReplicaReview::getEventOccurredAt)
            .containsExactly(ReviewFit.SMALL, false, hiddenAt);
    }

    // 핏 칸은 CHECK 제약이 허용하는 값만 저장한다. 커뮤니티가 핏 값을 새로 더하면
    // 이 제약을 먼저 고쳐야 받은 메시지가 DLT로 빠지지 않으므로, 모든 핏 값을 저장해 본다
    @ParameterizedTest(name = "{0}")
    @EnumSource(ReviewFit.class)
    @DisplayName("리뷰 복사본은 리뷰의 모든 핏 값을 저장한다")
    void saveEveryFit(ReviewFit fit) {
        replicaReviewService.applyReviewChanged(
            RecommendReplicaReviewFixture.reviewChangedEventAt(fit.name(), true, REVIEW_CHANGED_AT));

        assertThat(replicaReviewRepository.findById(REVIEW_ID)).get()
            .extracting(RecommendReplicaReview::getFit)
            .isEqualTo(fit);
    }

    @Test
    @DisplayName("FIT-12 핏을 남기지 않은 리뷰는 핏이 빈 리뷰 복사본으로 저장한다")
    void saveReviewWithoutFit() {
        replicaReviewService.applyReviewChanged(
            RecommendReplicaReviewFixture.reviewChangedEventAt(null, true, REVIEW_CHANGED_AT));

        assertThat(replicaReviewRepository.findById(REVIEW_ID)).get()
            .extracting(RecommendReplicaReview::getFit, RecommendReplicaReview::isVisible)
            .containsExactly(null, true);
    }

    @Test
    @DisplayName("계약에 없는 핏 값이 온 리뷰 변경됨은 IllegalArgumentException으로 거절하고 리뷰 복사본을 남기지 않는다")
    void rejectUnknownFit() {
        assertThatThrownBy(() -> replicaReviewService.applyReviewChanged(
            RecommendReplicaReviewFixture.reviewChangedEventAt("TOO_BIG", true, REVIEW_CHANGED_AT)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(replicaReviewRepository.findById(REVIEW_ID)).isEmpty();
    }

    @Test
    @DisplayName("FIT-12 주문상품으로 리뷰 핏을 찾으면 공개되고 핏을 남긴 리뷰만 나온다")
    void findVisibleFitsByOrderItemIds() {
        replicaReviewService.applyReviewChanged(RecommendReplicaReviewFixture.reviewChangedEvent(
            1L, 601L, PRODUCT_ID, "SMALL", true, REVIEW_CHANGED_AT));
        replicaReviewService.applyReviewChanged(RecommendReplicaReviewFixture.reviewChangedEvent(
            2L, 602L, PRODUCT_ID, "LARGE", false, REVIEW_CHANGED_AT));
        replicaReviewService.applyReviewChanged(RecommendReplicaReviewFixture.reviewChangedEvent(
            3L, 603L, PRODUCT_ID, null, true, REVIEW_CHANGED_AT));
        replicaReviewService.applyReviewChanged(RecommendReplicaReviewFixture.reviewChangedEvent(
            4L, 604L, PRODUCT_ID, "TRUE_TO_SIZE", true, REVIEW_CHANGED_AT));

        List<RecommendReplicaReview> found = replicaReviewRepository.findVisibleFitsByOrderItemIds(
            List.of(601L, 602L, 603L));

        assertThat(found)
            .extracting(RecommendReplicaReview::getOrderItemId, RecommendReplicaReview::countedFit)
            .containsExactly(tuple(601L, ReviewFit.SMALL));
    }

    @ParameterizedTest(name = "{0} {1}건")
    @CsvSource({
        "SMALL, 2",
        "TRUE_TO_SIZE, 0",
        "LARGE, 1"
    })
    @DisplayName("FIT-13 상품의 핏별 리뷰 수는 그 상품의 공개된 리뷰만 세고, 숨긴 리뷰 · 핏을 남기지 않은 리뷰 · 다른 상품의 리뷰는 세지 않는다")
    void countVisibleFitsOfProduct(ReviewFit fit, long expected) {
        Long otherProductId = PRODUCT_ID + 1;
        replicaReviewService.applyReviewChanged(RecommendReplicaReviewFixture.reviewChangedEvent(
            1L, 601L, PRODUCT_ID, "SMALL", true, REVIEW_CHANGED_AT));
        replicaReviewService.applyReviewChanged(RecommendReplicaReviewFixture.reviewChangedEvent(
            2L, 602L, PRODUCT_ID, "SMALL", true, REVIEW_CHANGED_AT));
        replicaReviewService.applyReviewChanged(RecommendReplicaReviewFixture.reviewChangedEvent(
            3L, 603L, PRODUCT_ID, "SMALL", false, REVIEW_CHANGED_AT));
        replicaReviewService.applyReviewChanged(RecommendReplicaReviewFixture.reviewChangedEvent(
            4L, 604L, PRODUCT_ID, "LARGE", true, REVIEW_CHANGED_AT));
        replicaReviewService.applyReviewChanged(RecommendReplicaReviewFixture.reviewChangedEvent(
            5L, 605L, PRODUCT_ID, null, true, REVIEW_CHANGED_AT));
        replicaReviewService.applyReviewChanged(RecommendReplicaReviewFixture.reviewChangedEvent(
            6L, 606L, otherProductId, "TRUE_TO_SIZE", true, REVIEW_CHANGED_AT));

        assertThat(replicaReviewRepository.countVisibleFits(PRODUCT_ID, fit)).isEqualTo(expected);
    }
}
