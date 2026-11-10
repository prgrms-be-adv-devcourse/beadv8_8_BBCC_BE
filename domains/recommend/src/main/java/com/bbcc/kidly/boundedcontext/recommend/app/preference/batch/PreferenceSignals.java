package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ConfirmedPurchase;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.RecentActivity;
import java.util.List;

/**
 * 읽은 활동 · 구매확정을 선호 계산에 넣는 신호로 바꾼다.
 */
public final class PreferenceSignals {

    private PreferenceSignals() {
    }

    public static List<PreferenceSignal> of(List<RecentActivity> activities, List<ConfirmedPurchase> purchases) {
        throw new UnsupportedOperationException();
    }
}
