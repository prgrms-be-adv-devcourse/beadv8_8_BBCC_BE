package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITE_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITE_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 찜 · 찜 해제 활동 기록을 만드는 규칙을 확인한다. 다른 활동 종류는 ActivityRecordTest가 확인한다.
 */
class ActivityRecordFavoriteTest {

    @Test
    @DisplayName("PREF-20 찜 값으로 자녀 없는 회원의 찜 활동 기록을 만들고 장바구니 · 수량 · 검색 칸은 비운다")
    void createFavoriteAdded() {
        ActivityRecord record = ActivityRecord.favoriteAdded(new ActivityRecord.FavoriteAddedInfo(
            FAVORITE_EVENT_ID, FAVORITE_ID, MEMBER_1_ID, PRODUCT_A_ID, FAVORITED_AT));

        assertThat(record)
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getFavoriteId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getOccurredAt,
                ActivityRecord::getCartItemId,
                ActivityRecord::getAddedQty,
                ActivityRecord::getKeyword)
            .containsExactly(ActivityType.FAVORITE_ADDED, FAVORITE_EVENT_ID, FAVORITE_ID, MEMBER_1_ID, null,
                PRODUCT_A_ID, FAVORITED_AT, null, null, null);
    }
}
