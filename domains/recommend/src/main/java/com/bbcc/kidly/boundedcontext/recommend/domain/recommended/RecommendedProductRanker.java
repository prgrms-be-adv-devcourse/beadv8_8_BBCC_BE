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

    // 점수가 같아도 요청마다 순서가 같아야 쪽을 넘길 때 상품이 겹치거나 빠지지 않는다
    private static final Comparator<ScoredProduct> RANK_ORDER =
            Comparator.comparingInt(ScoredProduct::score)
                    .thenComparing(scored -> scored.product().productRegisteredAt())
                    .thenComparing(scored -> scored.product().productId())
                    .reversed();

    public List<RecommendedProduct> rank(
            List<RecommendedProduct> products,
            ChildPreference preference,
            Set<Long> excludedProductIds,
            LocalDateTime baseTime
    ) {
        List<ScoredProduct> scored = new ArrayList<>();
        for (RecommendedProduct product : products) {
            if (excludedProductIds.contains(product.productId())) {
                continue;
            }
            // 목록을 보는 사이 등록된 상품이 끼어들면 다음 쪽에서 앞 쪽의 상품이 다시 나온다
            if (product.productRegisteredAt().isAfter(baseTime)) {
                continue;
            }
            scored.add(new ScoredProduct(product, scoreOf(product, preference, baseTime)));
        }
        scored.sort(RANK_ORDER);
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
