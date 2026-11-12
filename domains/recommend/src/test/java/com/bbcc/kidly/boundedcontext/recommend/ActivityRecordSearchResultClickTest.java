package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.CLICKED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.CLICK_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 검색 결과 클릭 활동 기록을 만드는 규칙을 확인한다. 다른 활동 종류는 ActivityRecordTest가 확인한다.
 */
class ActivityRecordSearchResultClickTest {

    @ParameterizedTest(name = "자녀 ID {0}")
    @MethodSource("childProfileIds")
    @DisplayName("PREF-17 검색 결과 클릭 값으로 클릭 종류의 활동 기록을 만들고 고른 자녀를 그대로 담으며 장바구니 · 수량 · 검색 칸은 비운다")
    void createSearchResultClicked(Long childProfileId) {
        ActivityRecord record = ActivityRecord.searchResultClicked(new ActivityRecord.SearchResultClickedInfo(
            CLICK_EVENT_ID, MEMBER_1_ID, childProfileId, PRODUCT_A_ID, CLICKED_AT));

        assertThat(record)
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getOccurredAt,
                ActivityRecord::getCartItemId,
                ActivityRecord::getProductOptionId,
                ActivityRecord::getAddedQty,
                ActivityRecord::getCartQty,
                ActivityRecord::getKeyword,
                ActivityRecord::getCategoryId)
            .containsExactly(ActivityType.SEARCH_RESULT_CLICKED, CLICK_EVENT_ID, MEMBER_1_ID, childProfileId,
                PRODUCT_A_ID, CLICKED_AT, null, null, null, null, null, null);
    }

    static Stream<Long> childProfileIds() {
        return Stream.of(CHILD_1_ID, null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clicksWithNull")
    @DisplayName("검색 결과 클릭 값에 필수 값이 없으면 활동 기록을 만들지 않는다")
    void rejectSearchResultClickedWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> clicksWithNull() {
        return Stream.of(
            Named.of("클릭 값", () -> ActivityRecord.searchResultClicked(null)),
            Named.of("이벤트 ID", () -> ActivityRecord.searchResultClicked(new ActivityRecord.SearchResultClickedInfo(
                null, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, CLICKED_AT))),
            Named.of("회원 ID", () -> ActivityRecord.searchResultClicked(new ActivityRecord.SearchResultClickedInfo(
                CLICK_EVENT_ID, null, CHILD_1_ID, PRODUCT_A_ID, CLICKED_AT))),
            Named.of("상품 ID", () -> ActivityRecord.searchResultClicked(new ActivityRecord.SearchResultClickedInfo(
                CLICK_EVENT_ID, MEMBER_1_ID, CHILD_1_ID, null, CLICKED_AT))),
            Named.of("누른 시각", () -> ActivityRecord.searchResultClicked(new ActivityRecord.SearchResultClickedInfo(
                CLICK_EVENT_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, null))));
    }
}
