package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("PREF-16 구매확정은 카테고리 없이 브랜드 · 상품 · 확정 시각을 담은 구매확정 신호가 된다")
    void purchasesBecomePurchaseConfirmedSignals() {
        List<ConfirmedPurchase> purchases = List.of(new ConfirmedPurchase(3L, "브랜드C", AT.minusDays(90)));

        assertThat(PreferenceSignals.of(List.of(), purchases)).containsExactly(
            new PreferenceSignal(PreferenceSignalType.PURCHASE_CONFIRMED, AT.minusDays(90), null, "브랜드C", 3L, false));
    }
}
