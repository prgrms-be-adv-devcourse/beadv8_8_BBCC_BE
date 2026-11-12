package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.WISHED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.WISH_CANCELLED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.WISH_CANCEL_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.WISH_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.WISH_REQUEST_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord.WishedItem;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 조르기 · 조르기 철회 활동 기록을 만드는 규칙을 확인한다. 다른 활동 종류는 ActivityRecordTest가 확인한다.
 */
class ActivityRecordWishTest {

    private static final WishedItem ITEM_31 = new WishedItem(31L, 32L);
    private static final WishedItem ITEM_41 = new WishedItem(41L, 42L);

    @Test
    @DisplayName("PREF-18 조른 상품마다 자녀의 조르기 활동 기록을 만들고 장바구니 · 수량 · 검색 칸은 비운다")
    void createWishSentPerItem() {
        List<ActivityRecord> records = ActivityRecord.wishSent(wishSent(ITEM_31, ITEM_41));

        assertThat(records)
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getWishRequestId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getProductOptionId,
                ActivityRecord::getOccurredAt,
                ActivityRecord::getCartItemId,
                ActivityRecord::getAddedQty,
                ActivityRecord::getCartQty,
                ActivityRecord::getKeyword)
            .containsExactly(
                tuple(ActivityType.WISH_SENT, WISH_REQUEST_ID, MEMBER_1_ID, CHILD_1_ID, 31L, 32L, WISHED_AT,
                    null, null, null, null),
                tuple(ActivityType.WISH_SENT, WISH_REQUEST_ID, MEMBER_1_ID, CHILD_1_ID, 41L, 42L, WISHED_AT,
                    null, null, null, null));
    }

    @Test
    @DisplayName("PREF-18 줄마다 이벤트 ID가 다르고, 같은 조르기로 다시 만들면 같은 ID가 나온다")
    void itemEventIdsAreDistinctAndStable() {
        List<ActivityRecord> first = ActivityRecord.wishSent(wishSent(ITEM_31, ITEM_41));
        List<ActivityRecord> again = ActivityRecord.wishSent(wishSent(ITEM_31, ITEM_41));

        assertThat(first).extracting(ActivityRecord::getEventId)
            .doesNotHaveDuplicates()
            .doesNotContain(WISH_EVENT_ID)
            .containsExactlyElementsOf(again.stream().map(ActivityRecord::getEventId).toList());
    }

    @Test
    @DisplayName("PREF-18 상품 ID가 없는 조른 상품은 건너뛰고, 남은 상품의 ID는 건너뛴 상품이 있어도 순번이 같아 바뀌지 않는다")
    void skipItemWithoutProductKeepingOrder() {
        List<ActivityRecord> withAll = ActivityRecord.wishSent(wishSent(ITEM_31, ITEM_41));

        List<ActivityRecord> skipped = ActivityRecord.wishSent(wishSent(new WishedItem(null, 32L), ITEM_41));

        assertThat(skipped).singleElement()
            .satisfies(record -> {
                assertThat(record.getProductId()).isEqualTo(41L);
                assertThat(record.getEventId()).isEqualTo(withAll.get(1).getEventId());
            });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wishesWithNull")
    @DisplayName("조르기 값에 필수 값이 없으면 활동 기록을 만들지 않는다")
    void rejectWishSentWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> wishesWithNull() {
        List<WishedItem> items = List.of(ITEM_31);
        return Stream.of(
            Named.of("조르기 값", () -> ActivityRecord.wishSent(null)),
            Named.of("이벤트 ID", () -> ActivityRecord.wishSent(new ActivityRecord.WishSentInfo(
                null, WISH_REQUEST_ID, MEMBER_1_ID, CHILD_1_ID, items, WISHED_AT))),
            Named.of("조르기 ID", () -> ActivityRecord.wishSent(new ActivityRecord.WishSentInfo(
                WISH_EVENT_ID, null, MEMBER_1_ID, CHILD_1_ID, items, WISHED_AT))),
            Named.of("회원 ID", () -> ActivityRecord.wishSent(new ActivityRecord.WishSentInfo(
                WISH_EVENT_ID, WISH_REQUEST_ID, null, CHILD_1_ID, items, WISHED_AT))),
            Named.of("자녀 ID", () -> ActivityRecord.wishSent(new ActivityRecord.WishSentInfo(
                WISH_EVENT_ID, WISH_REQUEST_ID, MEMBER_1_ID, null, items, WISHED_AT))),
            Named.of("조른 상품 목록", () -> ActivityRecord.wishSent(new ActivityRecord.WishSentInfo(
                WISH_EVENT_ID, WISH_REQUEST_ID, MEMBER_1_ID, CHILD_1_ID, null, WISHED_AT))),
            Named.of("조른 상품", () -> ActivityRecord.wishSent(wishSent((WishedItem) null))),
            Named.of("옵션 ID", () -> ActivityRecord.wishSent(wishSent(new WishedItem(31L, null)))),
            Named.of("조른 시각", () -> ActivityRecord.wishSent(new ActivityRecord.WishSentInfo(
                WISH_EVENT_ID, WISH_REQUEST_ID, MEMBER_1_ID, CHILD_1_ID, items, null))));
    }

    @Test
    @DisplayName("PREF-19 조르기 철회 값으로 상품 없이 그 조르기의 철회 활동 기록을 만든다")
    void createWishCancelled() {
        ActivityRecord record = ActivityRecord.wishCancelled(new ActivityRecord.WishCancelledInfo(
            WISH_CANCEL_EVENT_ID, WISH_REQUEST_ID, MEMBER_1_ID, CHILD_1_ID, WISH_CANCELLED_AT));

        assertThat(record)
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getWishRequestId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getProductOptionId,
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.WISH_CANCELLED, WISH_CANCEL_EVENT_ID, WISH_REQUEST_ID, MEMBER_1_ID,
                CHILD_1_ID, null, null, WISH_CANCELLED_AT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cancellationsWithNull")
    @DisplayName("조르기 철회 값에 필수 값이 없으면 활동 기록을 만들지 않는다")
    void rejectWishCancelledWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> cancellationsWithNull() {
        return Stream.of(
            Named.of("철회 값", () -> ActivityRecord.wishCancelled(null)),
            Named.of("이벤트 ID", () -> ActivityRecord.wishCancelled(new ActivityRecord.WishCancelledInfo(
                null, WISH_REQUEST_ID, MEMBER_1_ID, CHILD_1_ID, WISH_CANCELLED_AT))),
            Named.of("조르기 ID", () -> ActivityRecord.wishCancelled(new ActivityRecord.WishCancelledInfo(
                WISH_CANCEL_EVENT_ID, null, MEMBER_1_ID, CHILD_1_ID, WISH_CANCELLED_AT))),
            Named.of("회원 ID", () -> ActivityRecord.wishCancelled(new ActivityRecord.WishCancelledInfo(
                WISH_CANCEL_EVENT_ID, WISH_REQUEST_ID, null, CHILD_1_ID, WISH_CANCELLED_AT))),
            Named.of("자녀 ID", () -> ActivityRecord.wishCancelled(new ActivityRecord.WishCancelledInfo(
                WISH_CANCEL_EVENT_ID, WISH_REQUEST_ID, MEMBER_1_ID, null, WISH_CANCELLED_AT))),
            Named.of("철회 시각", () -> ActivityRecord.wishCancelled(new ActivityRecord.WishCancelledInfo(
                WISH_CANCEL_EVENT_ID, WISH_REQUEST_ID, MEMBER_1_ID, CHILD_1_ID, null))));
    }

    @ParameterizedTest(name = "철회 원인 {0}")
    @NullSource
    @ValueSource(strings = "DIRECT")
    @DisplayName("PREF-19 보낸 자녀가 직접 철회했거나 원인이 없는 예전 메시지면 자녀가 거둔 철회다")
    void directCancellationIsWithdrawnByChild(String cancelReason) {
        assertThat(ActivityRecord.isWithdrawnByChild(cancelReason)).isTrue();
    }

    @ParameterizedTest(name = "철회 원인 {0}")
    @ValueSource(strings = {"CHILD_DELETED", "ACCOUNT_DELETED", "SYSTEM", "GUARDIAN"})
    @DisplayName("PREF-19 자녀 · 계정 삭제 · 시스템 철회와 모르는 원인은 자녀가 거둔 철회가 아니다")
    void otherCancellationIsNotWithdrawnByChild(String cancelReason) {
        assertThat(ActivityRecord.isWithdrawnByChild(cancelReason)).isFalse();
    }

    private static ActivityRecord.WishSentInfo wishSent(WishedItem... items) {
        return new ActivityRecord.WishSentInfo(WISH_EVENT_ID, WISH_REQUEST_ID, MEMBER_1_ID, CHILD_1_ID,
            Arrays.asList(items), WISHED_AT);
    }
}
