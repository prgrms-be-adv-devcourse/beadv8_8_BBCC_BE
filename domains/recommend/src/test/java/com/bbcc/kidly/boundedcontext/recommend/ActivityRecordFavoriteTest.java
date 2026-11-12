package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITE_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITE_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITE_REMOVED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.FAVORITE_REMOVE_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord.FavoriteInfo;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.FavoriteRemoval;
import java.util.List;
import java.util.function.Function;
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
 * 찜 · 찜 해제 활동 기록을 만드는 규칙을 확인한다. 다른 활동 종류는 ActivityRecordTest가 확인한다.
 */
class ActivityRecordFavoriteTest {

    @Test
    @DisplayName("PREF-20 찜 값으로 자녀 없는 회원의 찜 활동 기록을 만들고 장바구니 · 수량 · 검색 칸은 비운다")
    void createFavoriteAdded() {
        ActivityRecord record = ActivityRecord.favoriteAdded(new ActivityRecord.FavoriteInfo(
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

    @Test
    @DisplayName("PREF-21 찜 해제 값으로 그 찜의 해제 활동 기록을 만든다")
    void createFavoriteRemoved() {
        ActivityRecord record = ActivityRecord.favoriteRemoved(new ActivityRecord.FavoriteInfo(
            FAVORITE_REMOVE_EVENT_ID, FAVORITE_ID, MEMBER_1_ID, PRODUCT_A_ID, FAVORITE_REMOVED_AT));

        assertThat(record)
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getFavoriteId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.FAVORITE_REMOVED, FAVORITE_REMOVE_EVENT_ID, FAVORITE_ID, MEMBER_1_ID,
                null, PRODUCT_A_ID, FAVORITE_REMOVED_AT);
    }

    @Test
    @DisplayName("PREF-21 회원이 직접 해제한 찜(USER)은 관심을 거둔 찜이다")
    void userRemovalIsWithdrawnByMember() {
        assertThat(FavoriteRemoval.isWithdrawnByMember("USER")).isTrue();
    }

    @ParameterizedTest(name = "해제 원인 {0}")
    @NullSource
    @ValueSource(strings = {"MEMBER_WITHDRAWN", "ADMIN"})
    @DisplayName("PREF-21 탈퇴로 지운 찜과 USER가 아닌 원인은 관심을 거둔 찜으로 보지 않는다")
    void otherRemovalIsNotWithdrawnByMember(String cause) {
        assertThat(FavoriteRemoval.isWithdrawnByMember(cause)).isFalse();
    }

    @Test
    @DisplayName("PREF-21 회원 탈퇴로 지운 찜(MEMBER_WITHDRAWN)이면 늦게 온 그 찜 기록을 지울 대상이다")
    void withdrawalRemovalIsByWithdrawal() {
        assertThat(FavoriteRemoval.isByWithdrawal("MEMBER_WITHDRAWN")).isTrue();
    }

    @ParameterizedTest(name = "해제 원인 {0}")
    @NullSource
    @ValueSource(strings = "USER")
    @DisplayName("PREF-21 회원이 직접 해제한 찜이나 원인이 없으면 지울 대상이 아니다")
    void otherRemovalIsNotByWithdrawal(String cause) {
        assertThat(FavoriteRemoval.isByWithdrawal(cause)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("favoritesWithNull")
    @DisplayName("찜 · 찜 해제 값에 필수 값이 없으면 활동 기록을 만들지 않는다")
    void rejectFavoriteWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> favoritesWithNull() {
        Stream.Builder<Named<ThrowingCallable>> cases = Stream.builder();
        for (Named<Function<FavoriteInfo, ActivityRecord>> factory : factories()) {
            String name = factory.getName();
            Function<FavoriteInfo, ActivityRecord> create = factory.getPayload();
            cases.add(Named.of(name + " 값", () -> create.apply(null)));
            cases.add(Named.of(name + " 이벤트 ID", () -> create.apply(new FavoriteInfo(
                null, FAVORITE_ID, MEMBER_1_ID, PRODUCT_A_ID, FAVORITED_AT))));
            cases.add(Named.of(name + " 찜 ID", () -> create.apply(new FavoriteInfo(
                FAVORITE_EVENT_ID, null, MEMBER_1_ID, PRODUCT_A_ID, FAVORITED_AT))));
            cases.add(Named.of(name + " 회원 ID", () -> create.apply(new FavoriteInfo(
                FAVORITE_EVENT_ID, FAVORITE_ID, null, PRODUCT_A_ID, FAVORITED_AT))));
            cases.add(Named.of(name + " 상품 ID", () -> create.apply(new FavoriteInfo(
                FAVORITE_EVENT_ID, FAVORITE_ID, MEMBER_1_ID, null, FAVORITED_AT))));
            cases.add(Named.of(name + " 시각", () -> create.apply(new FavoriteInfo(
                FAVORITE_EVENT_ID, FAVORITE_ID, MEMBER_1_ID, PRODUCT_A_ID, null))));
        }
        return cases.build();
    }

    private static List<Named<Function<FavoriteInfo, ActivityRecord>>> factories() {
        return List.of(
            Named.of("찜", ActivityRecord::favoriteAdded),
            Named.of("찜 해제", ActivityRecord::favoriteRemoved));
    }
}
