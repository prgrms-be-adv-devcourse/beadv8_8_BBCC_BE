package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.WISHED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.WISH_CANCELLED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.WISH_CANCEL_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.WISH_REQUEST_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.wishCancelledEvent;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.wishSentEvent;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.app.activity.ActivityRecordService;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.shared.order.event.WishSentEvent;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 활동 기록 서비스가 조르기 · 조르기 철회를 실제 DB에 저장하는 규칙을 확인한다.
 * Kafka로 받는 연결은 RecommendWishKafkaIntegrationTest가 확인한다.
 */
@IntegrationTest
class WishRecordIntegrationTest {

    // 저장된 기록 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaActivityRecordRepository activityRecordRepository;

    @Autowired
    private ActivityRecordService activityRecordService;

    @Test
    @DisplayName("PREF-18 조르기 이벤트를 조른 상품마다 자녀의 조르기 활동 기록 한 줄로 저장한다")
    void saveWishSentPerItem() {
        activityRecordService.recordWishSent(wishSentEvent(
            new WishSentEvent.Item(31L, 32L, 1),
            new WishSentEvent.Item(41L, 42L, 2)));

        assertThat(activityRecordRepository.findAll())
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getWishRequestId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getProductOptionId,
                ActivityRecord::getOccurredAt)
            .containsExactlyInAnyOrder(
                tuple(ActivityType.WISH_SENT, WISH_REQUEST_ID, MEMBER_1_ID, CHILD_1_ID, 31L, 32L, WISHED_AT),
                tuple(ActivityType.WISH_SENT, WISH_REQUEST_ID, MEMBER_1_ID, CHILD_1_ID, 41L, 42L, WISHED_AT));
    }

    @Test
    @DisplayName("PREF-18 같은 조르기 이벤트를 다시 받아도 예외 없이 조른 상품마다 한 줄이다")
    void keepOneRecordPerItemForSameWishEvent() {
        WishSentEvent event = wishSentEvent(new WishSentEvent.Item(31L, 32L, 1), new WishSentEvent.Item(41L, 42L, 2));
        activityRecordService.recordWishSent(event);

        assertThatCode(() -> activityRecordService.recordWishSent(event)).doesNotThrowAnyException();

        assertThat(activityRecordRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("PREF-18 상품 ID가 없는 조른 상품은 카테고리 · 브랜드 · 벡터를 알 수 없어 건너뛰고 나머지만 기록한다")
    void skipWishItemWithoutProduct() {
        activityRecordService.recordWishSent(wishSentEvent(
            new WishSentEvent.Item(null, 32L, 1),
            new WishSentEvent.Item(41L, 42L, 2)));

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(ActivityRecord::getProductId, ActivityRecord::getProductOptionId)
            .containsExactly(41L, 42L);
    }

    @ParameterizedTest(name = "철회 원인 {0}")
    @NullSource
    @ValueSource(strings = "DIRECT")
    @DisplayName("PREF-19 보낸 자녀가 직접 철회한 조르기(원인이 없는 예전 메시지 포함)는 그 조르기의 철회 기록 한 줄을 남긴다")
    void saveDirectWishCancellation(String cancelReason) {
        activityRecordService.recordWishCancelled(wishCancelledEvent(cancelReason));

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getWishRequestId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.WISH_CANCELLED, WISH_CANCEL_EVENT_ID, WISH_REQUEST_ID, MEMBER_1_ID,
                CHILD_1_ID, null, WISH_CANCELLED_AT);
    }

    @ParameterizedTest(name = "철회 원인 {0}")
    @ValueSource(strings = {"CHILD_DELETED", "ACCOUNT_DELETED", "SYSTEM"})
    @DisplayName("PREF-19 자녀 · 계정 삭제나 시스템이 철회한 조르기는 자녀가 거둔 것이 아니어서 기록하지 않는다")
    void skipWishCancellationNotByChild(String cancelReason) {
        activityRecordService.recordWishCancelled(wishCancelledEvent(cancelReason));

        assertThat(activityRecordRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("PREF-19 같은 조르기 철회 이벤트를 다시 받아도 예외 없이 철회 기록은 한 줄이다")
    void keepOneRecordForSameWishCancellation() {
        activityRecordService.recordWishCancelled(wishCancelledEvent("DIRECT"));

        assertThatCode(() -> activityRecordService.recordWishCancelled(wishCancelledEvent("DIRECT")))
            .doesNotThrowAnyException();

        assertThat(activityRecordRepository.findAll()).hasSize(1);
    }
}
