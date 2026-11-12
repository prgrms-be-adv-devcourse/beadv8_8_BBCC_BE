package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.PreferenceSignals;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ConfirmedPurchase;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignalType;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.RecentActivity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreferenceSignalsTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 10, 3, 0);

    @Test
    @DisplayName("PREF-07 활동은 같은 이름의 신호가 되고 장바구니에서 뺐는지는 그대로 옮겨진다")
    void activitiesKeepRemovedFlag() {
        List<RecentActivity> activities = List.of(
            new RecentActivity(ActivityType.SEARCH, null, 10L, null, AT, false),
            new RecentActivity(ActivityType.PRODUCT_VIEWED, 1L, 11L, "브랜드A", AT.minusDays(1), false),
            new RecentActivity(ActivityType.CART_ITEM_ADDED, 2L, 12L, "브랜드B", AT.minusDays(2), true));

        assertThat(PreferenceSignals.of(activities, List.of())).containsExactly(
            new PreferenceSignal(PreferenceSignalType.SEARCH, AT, 10L, null, null, false),
            new PreferenceSignal(PreferenceSignalType.PRODUCT_VIEWED, AT.minusDays(1), 11L, "브랜드A", 1L, false),
            new PreferenceSignal(PreferenceSignalType.CART_ITEM_ADDED, AT.minusDays(2), 12L, "브랜드B", 2L, true));
    }

    @Test
    @DisplayName("PREF-17 검색 결과 클릭 활동은 누른 상품의 카테고리 · 브랜드를 담은 검색 결과 클릭 신호가 된다")
    void searchResultClickBecomesClickSignal() {
        List<RecentActivity> activities = List.of(
            new RecentActivity(ActivityType.SEARCH_RESULT_CLICKED, 4L, 13L, "브랜드D", AT, false));

        assertThat(PreferenceSignals.of(activities, List.of())).containsExactly(
            new PreferenceSignal(PreferenceSignalType.SEARCH_RESULT_CLICKED, AT, 13L, "브랜드D", 4L, false));
    }

    @Test
    @DisplayName("PREF-19 조르기 활동은 조른 상품을 담은 조르기 신호가 되고 철회했는지는 그대로 옮겨진다")
    void wishSentBecomesWishSignalWithWithdrawnFlag() {
        List<RecentActivity> activities = List.of(
            new RecentActivity(ActivityType.WISH_SENT, 5L, 14L, "브랜드E", AT, false),
            new RecentActivity(ActivityType.WISH_SENT, 6L, 15L, "브랜드F", AT, true));

        assertThat(PreferenceSignals.of(activities, List.of())).containsExactly(
            new PreferenceSignal(PreferenceSignalType.WISH_SENT, AT, 14L, "브랜드E", 5L, false),
            new PreferenceSignal(PreferenceSignalType.WISH_SENT, AT, 15L, "브랜드F", 6L, true));
    }

    @Test
    @DisplayName("PREF-21 찜 활동은 찜한 상품을 담은 찜 신호가 되고 해제했는지는 그대로 옮겨진다")
    void favoriteBecomesFavoriteSignalWithWithdrawnFlag() {
        List<RecentActivity> activities = List.of(
            new RecentActivity(ActivityType.FAVORITE_ADDED, 7L, 16L, "브랜드G", AT, false),
            new RecentActivity(ActivityType.FAVORITE_ADDED, 8L, 17L, "브랜드H", AT, true));

        assertThat(PreferenceSignals.of(activities, List.of())).containsExactly(
            new PreferenceSignal(PreferenceSignalType.FAVORITE_ADDED, AT, 16L, "브랜드G", 7L, false),
            new PreferenceSignal(PreferenceSignalType.FAVORITE_ADDED, AT, 17L, "브랜드H", 8L, true));
    }

    @Test
    @DisplayName("PREF-16 구매확정은 카테고리 없이 브랜드 · 상품 · 확정 시각을 담은 구매확정 신호가 된다")
    void purchasesBecomePurchaseConfirmedSignals() {
        List<ConfirmedPurchase> purchases = List.of(new ConfirmedPurchase(3L, "브랜드C", AT.minusDays(90)));

        assertThat(PreferenceSignals.of(List.of(), purchases)).containsExactly(
            new PreferenceSignal(PreferenceSignalType.PURCHASE_CONFIRMED, AT.minusDays(90), null, "브랜드C", 3L, false));
    }

    @Test
    @DisplayName("장바구니 빼기처럼 신호가 없는 활동 종류가 오면 계산하지 않고 실패한다")
    void rejectUnsupportedActivityType() {
        List<RecentActivity> activities = List.of(
            new RecentActivity(ActivityType.CART_ITEM_REMOVED, 2L, 12L, "브랜드B", AT, false));

        assertThatThrownBy(() -> PreferenceSignals.of(activities, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
