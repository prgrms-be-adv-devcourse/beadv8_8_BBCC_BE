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
        // 방금 산 종류가 목록 위를 차지하지 않게 구매확정에는 카테고리를 담지 않는다
        for (ConfirmedPurchase purchase : purchases) {
            signals.add(new PreferenceSignal(PreferenceSignalType.PURCHASE_CONFIRMED, purchase.confirmedAt(), null,
                purchase.brand(), purchase.productId(), false));
        }
        return signals;
    }

    private static PreferenceSignalType signalTypeOf(ActivityType type) {
        return switch (type) {
            case SEARCH -> PreferenceSignalType.SEARCH;
            case PRODUCT_VIEWED -> PreferenceSignalType.PRODUCT_VIEWED;
            case CART_ITEM_ADDED -> PreferenceSignalType.CART_ITEM_ADDED;
            // 읽기 포트는 장바구니 빼기를 담기 기록의 표시로만 돌려준다. 그 밖의 종류가 오면 읽기가 잘못된 것이다
            default -> throw new IllegalArgumentException("선호 신호로 바꿀 수 없는 활동 종류입니다: " + type);
        };
    }
}
