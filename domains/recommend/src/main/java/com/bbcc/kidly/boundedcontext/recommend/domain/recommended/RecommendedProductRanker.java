package com.bbcc.kidly.boundedcontext.recommend.domain.recommended;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceWeights;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 추천 목록의 상품에 선호 점수를 매기고, 점수 순서로 목록을 정렬한다.
 * 한 목록 안에서는 스냅숏에 취향 벡터가 있는지에 따라 정한 식 하나만 쓴다.
 */
public class RecommendedProductRanker {

    private static final double SECONDS_PER_DAY = 86_400.0;

    // 점수가 같아도 요청마다 순서가 같아야 쪽을 넘길 때 상품이 겹치거나 빠지지 않는다
    private static final Comparator<ScoredProduct> RANK_ORDER =
            Comparator.comparingDouble(ScoredProduct::score)
                    .thenComparing(scored -> scored.product().productRegisteredAt())
                    .thenComparing(scored -> scored.product().productId())
                    .reversed();

    /**
     * similarityByProductId는 상품마다 1 − (취향 벡터와 상품 벡터의 코사인 거리)다. 상품 벡터가 없는 상품은 들어 있지 않다.
     */
    public List<RecommendedProduct> rank(
            List<RecommendedProduct> products,
            PreferenceSnapshot snapshot,
            Map<Long, Double> similarityByProductId,
            Set<Long> excludedProductIds,
            LocalDateTime asOf
    ) {
        requireNonNull(excludedProductIds, "excludedProductIds는 필수입니다");
        List<ScoredProduct> scored = new ArrayList<>();
        for (RecommendedProduct product : products) {
            if (excludedProductIds.contains(product.productId())) {
                continue;
            }
            // 목록을 보는 사이 등록된 상품이 끼어들면 다음 쪽에서 앞 쪽의 상품이 다시 나온다
            if (product.productRegisteredAt().isAfter(asOf)) {
                continue;
            }
            scored.add(new ScoredProduct(product, scoreOf(product, snapshot, similarityByProductId, asOf)));
        }
        scored.sort(RANK_ORDER);
        List<RecommendedProduct> ranked = new ArrayList<>();
        for (ScoredProduct scoredProduct : scored) {
            ranked.add(scoredProduct.product());
        }
        return ranked;
    }

    /**
     * 상품 하나의 점수다. 소수 넷째 자리로 반올림한 값이다.
     */
    public double scoreOf(
            RecommendedProduct product,
            PreferenceSnapshot snapshot,
            Map<Long, Double> similarityByProductId,
            LocalDateTime asOf
    ) {
        requireNonNull(snapshot, "snapshot은 필수입니다");
        requireNonNull(similarityByProductId, "similarityByProductId는 필수입니다");
        requireNonNull(asOf, "asOf는 필수입니다");
        double category = snapshot.categoryAffinity(product.categoryId());
        double brand = snapshot.brandAffinity(product.brand());
        double freshness = freshnessOf(product, asOf);
        if (snapshot.tasteVector() == null) {
            return round(PreferenceWeights.RULE_CATEGORY_WEIGHT * category
                    + PreferenceWeights.RULE_BRAND_WEIGHT * brand
                    + PreferenceWeights.RULE_FRESHNESS_WEIGHT * freshness);
        }
        double tasteSimilarity = tasteSimilarityOf(product, category, similarityByProductId);
        return round(PreferenceWeights.TASTE_SIMILARITY_WEIGHT * tasteSimilarity
                + PreferenceWeights.TASTE_CATEGORY_WEIGHT * category
                + PreferenceWeights.TASTE_BRAND_WEIGHT * brand
                + PreferenceWeights.TASTE_FRESHNESS_WEIGHT * freshness);
    }

    private double tasteSimilarityOf(
            RecommendedProduct product, double categoryAffinity, Map<Long, Double> similarityByProductId) {
        Double similarity = similarityByProductId.get(product.productId());
        // 임베딩 전의 새 상품을 0으로 두면 임베딩이 끝날 때까지 맨 아래로 밀린다
        if (similarity == null) {
            return categoryAffinity;
        }
        return Math.max(0, similarity);
    }

    private double freshnessOf(RecommendedProduct product, LocalDateTime asOf) {
        double elapsedDays = Duration.between(product.productRegisteredAt(), asOf).toSeconds() / SECONDS_PER_DAY;
        if (elapsedDays < 0 || elapsedDays >= PreferenceWeights.FRESHNESS_DAYS) {
            return 0;
        }
        return 1 - elapsedDays / PreferenceWeights.FRESHNESS_DAYS;
    }

    private double round(double score) {
        return BigDecimal.valueOf(score).setScale(PreferenceWeights.SCORE_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    private record ScoredProduct(RecommendedProduct product, double score) {
    }
}
