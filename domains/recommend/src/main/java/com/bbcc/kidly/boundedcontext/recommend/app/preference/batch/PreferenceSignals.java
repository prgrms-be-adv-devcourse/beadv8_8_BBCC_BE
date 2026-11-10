package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ConfirmedPurchase;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignalType;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.RecentActivity;
import java.util.ArrayList;
import java.util.List;

/**
 * 읽은 활동 · 구매확정을 선호 계산에 넣는 신호로 바꾼다.
 */
public final class PreferenceSignals {

    private PreferenceSignals() {
    }

    public static List<PreferenceSignal> of(List<RecentActivity> activities, List<ConfirmedPurchase> purchases) {
        List<PreferenceSignal> signals = new ArrayList<>();
        for (RecentActivity activity : activities) {
            signals.add(new PreferenceSignal(signalTypeOf(activity.type()), activity.occurredAt(),
                activity.categoryId(), activity.brand(), activity.productId(), activity.removedFromCart()));
        }
        return signals;
    }

    private static PreferenceSignalType signalTypeOf(ActivityType type) {
        return switch (type) {
            case SEARCH -> PreferenceSignalType.SEARCH;
            case PRODUCT_VIEWED -> PreferenceSignalType.PRODUCT_VIEWED;
            case CART_ITEM_ADDED -> PreferenceSignalType.CART_ITEM_ADDED;
            default -> throw new UnsupportedOperationException();
        };
    }
}
