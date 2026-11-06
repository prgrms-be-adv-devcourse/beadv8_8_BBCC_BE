package com.bbcc.kidly.boundedcontext.recommend.domain.recommended;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreference;
import java.time.LocalDateTime;

/**
 * 추천 목록의 상품에 자녀의 선호 점수를 매긴다.
 */
public class RecommendedProductRanker {

    private static final int RECENT_DAYS = 7;

    private static final int RECENT_POINTS = 1;

    public int scoreOf(RecommendedProduct product, ChildPreference preference, LocalDateTime baseTime) {
        if (isRecent(product, baseTime)) {
            return RECENT_POINTS;
        }
        return 0;
    }

    private boolean isRecent(RecommendedProduct product, LocalDateTime baseTime) {
        LocalDateTime registeredAt = product.productRegisteredAt();
        return !registeredAt.isBefore(baseTime.minusDays(RECENT_DAYS)) && !registeredAt.isAfter(baseTime);
    }
}
