package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.QUICK_VIEWED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.QUICK_VIEW_EVENT_ID;
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
 * 담기 팝업을 연 것(팝업 조회)의 활동 기록을 만드는 규칙을 확인한다. 다른 활동 종류는 ActivityRecordTest가 확인한다.
 */
class ActivityRecordProductQuickViewTest {

    @ParameterizedTest(name = "자녀 ID {0}")
    @MethodSource("childProfileIds")
    @DisplayName("PREF-22 팝업 조회 값으로 팝업 조회 종류의 활동 기록을 만들고 고른 자녀를 그대로 담으며 장바구니 · 수량 · 검색 칸은 비운다")
    void createProductQuickViewed(Long childProfileId) {
        ActivityRecord record = ActivityRecord.productQuickViewed(new ActivityRecord.ProductViewedInfo(
            QUICK_VIEW_EVENT_ID, MEMBER_1_ID, childProfileId, PRODUCT_A_ID, QUICK_VIEWED_AT));

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
            .containsExactly(ActivityType.PRODUCT_QUICK_VIEWED, QUICK_VIEW_EVENT_ID, MEMBER_1_ID, childProfileId,
                PRODUCT_A_ID, QUICK_VIEWED_AT, null, null, null, null, null, null);
    }

    static Stream<Long> childProfileIds() {
        return Stream.of(CHILD_1_ID, null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("quickViewsWithNull")
    @DisplayName("팝업 조회 값에 필수 값이 없으면 활동 기록을 만들지 않는다")
    void rejectProductQuickViewedWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> quickViewsWithNull() {
        return Stream.of(
            Named.of("팝업 조회 값", () -> ActivityRecord.productQuickViewed(null)),
            Named.of("이벤트 ID", () -> ActivityRecord.productQuickViewed(new ActivityRecord.ProductViewedInfo(
                null, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, QUICK_VIEWED_AT))),
            Named.of("회원 ID", () -> ActivityRecord.productQuickViewed(new ActivityRecord.ProductViewedInfo(
                QUICK_VIEW_EVENT_ID, null, CHILD_1_ID, PRODUCT_A_ID, QUICK_VIEWED_AT))),
            Named.of("상품 ID", () -> ActivityRecord.productQuickViewed(new ActivityRecord.ProductViewedInfo(
                QUICK_VIEW_EVENT_ID, MEMBER_1_ID, CHILD_1_ID, null, QUICK_VIEWED_AT))),
            Named.of("연 시각", () -> ActivityRecord.productQuickViewed(new ActivityRecord.ProductViewedInfo(
                QUICK_VIEW_EVENT_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, null))));
    }
}
