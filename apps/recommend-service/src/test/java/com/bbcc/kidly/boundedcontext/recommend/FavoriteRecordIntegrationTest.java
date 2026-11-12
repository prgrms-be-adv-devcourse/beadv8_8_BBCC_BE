package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITE_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITE_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITE_REMOVED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITE_REMOVE_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.favoriteAddedEvent;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.favoriteRemovedEvent;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.recommend.app.activity.ActivityRecordService;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.shared.community.event.FavoriteRemovalCause;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 활동 기록 서비스가 찜 · 찜 해제를 실제 DB에 저장하는 규칙을 확인한다.
 * Kafka로 받는 연결은 RecommendCommunityKafkaIntegrationTest가 확인한다.
 */
@IntegrationTest
class FavoriteRecordIntegrationTest {

    // 저장된 기록 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaActivityRecordRepository activityRecordRepository;

    @Autowired
    private ActivityRecordService activityRecordService;

    @Test
    @DisplayName("PREF-20 찜 이벤트를 자녀 없는 회원의 찜 활동 기록으로 저장한다")
    void saveFavoriteAdded() {
        activityRecordService.recordFavoriteAdded(favoriteAddedEvent());

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getFavoriteId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.FAVORITE_ADDED, FAVORITE_EVENT_ID, FAVORITE_ID, MEMBER_1_ID, null,
                PRODUCT_A_ID, FAVORITED_AT);
    }

    @Test
    @DisplayName("PREF-20 같은 찜 이벤트를 다시 받아도 예외 없이 찜 활동 기록은 한 줄이다")
    void keepOneRecordForSameFavoriteEvent() {
        activityRecordService.recordFavoriteAdded(favoriteAddedEvent());

        assertThatCode(() -> activityRecordService.recordFavoriteAdded(favoriteAddedEvent()))
            .doesNotThrowAnyException();

        assertThat(activityRecordRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("PREF-21 회원이 직접 해제한 찜은 그 찜의 해제 기록 한 줄을 남기고, 다시 받아도 한 줄이다")
    void saveFavoriteRemovedByUser() {
        activityRecordService.recordFavoriteRemoved(favoriteRemovedEvent(FavoriteRemovalCause.USER));
        activityRecordService.recordFavoriteRemoved(favoriteRemovedEvent(FavoriteRemovalCause.USER));

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getFavoriteId,
                ActivityRecord::getMemberId,
                ActivityRecord::getProductId,
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.FAVORITE_REMOVED, FAVORITE_REMOVE_EVENT_ID, FAVORITE_ID, MEMBER_1_ID,
                PRODUCT_A_ID, FAVORITE_REMOVED_AT);
    }

    @Test
    @DisplayName("PREF-21 회원 탈퇴로 지운 찜은 해제 기록을 남기지 않고, 탈퇴 처리 뒤에 늦게 온 그 찜 기록도 지운다")
    void deleteFavoriteRemovedByWithdrawal() {
        activityRecordService.recordFavoriteAdded(favoriteAddedEvent());

        activityRecordService.recordFavoriteRemoved(favoriteRemovedEvent(FavoriteRemovalCause.MEMBER_WITHDRAWN));
        activityRecordService.recordFavoriteRemoved(favoriteRemovedEvent(FavoriteRemovalCause.MEMBER_WITHDRAWN));

        assertThat(activityRecordRepository.findAll()).isEmpty();
    }
}
