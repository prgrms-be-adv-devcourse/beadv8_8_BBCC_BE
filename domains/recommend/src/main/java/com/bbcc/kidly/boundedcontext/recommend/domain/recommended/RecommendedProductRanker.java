package com.bbcc.kidly.boundedcontext.recommend.domain.recommended;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreference;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 추천 목록의 상품에 자녀의 선호 점수를 매기고, 점수 순서로 목록을 정렬한다.
 */
public class RecommendedProductRanker {

    private static final int RECENT_DAYS = 7;

    private static final int RECENT_POINTS = 1;

    private static final Comparator<ScoredProduct> HIGHER_SCORE_FIRST =
            Comparator.comparingInt(ScoredProduct::score).reversed();

    public List<RecommendedProduct> rank(
            List<RecommendedProduct> products,
            ChildPreference preference,
            Set<Long> excludedProductIds,
            LocalDateTime baseTime
    ) {
        List<ScoredProduct> scored = new ArrayList<>();
        for (RecommendedProduct product : products) {
            scored.add(new ScoredProduct(product, scoreOf(product, preference, baseTime)));
        }
        scored.sort(HIGHER_SCORE_FIRST);
        List<RecommendedProduct> ranked = new ArrayList<>();
        for (ScoredProduct scoredProduct : scored) {
            ranked.add(scoredProduct.product());
        }
        return ranked;
    }

    public int scoreOf(RecommendedProduct product, ChildPreference preference, LocalDateTime baseTime) {
        int score = preference.categoryPoints(product.categoryId()) + preference.brandPoints(product.brand());
        if (isRecent(product, baseTime)) {
            score += RECENT_POINTS;
        }
        return score;
    }

    private boolean isRecent(RecommendedProduct product, LocalDateTime baseTime) {
        LocalDateTime registeredAt = product.productRegisteredAt();
        return !registeredAt.isBefore(baseTime.minusDays(RECENT_DAYS)) && !registeredAt.isAfter(baseTime);
    }

    private record ScoredProduct(RecommendedProduct product, int score) {
    }
}
