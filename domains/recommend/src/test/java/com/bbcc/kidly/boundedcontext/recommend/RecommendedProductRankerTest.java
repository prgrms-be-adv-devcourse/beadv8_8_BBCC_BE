package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.EmbeddingVectorFixture.axis;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.BASE_TIME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.OLD_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.recommendedProduct;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.recommendedProductRegisteredAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.BrandAffinity;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.CategoryAffinity;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.TasteVector;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProductRanker;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecommendedProductRankerTest {

    private static final Long CATEGORY_ID = 52L;

    private static final String BRAND = "키들리";

    private static final Long PRODUCT_ID = 1L;

    private static final Map<Long, Double> NO_SIMILARITIES = Map.of();

    private final RecommendedProductRanker ranker = new RecommendedProductRanker();

    @ParameterizedTest(name = "기준 시각 {0}초 전 등록 → 최신성 {1}")
    @CsvSource({
        "0, 1.0",
        "302400, 0.5",
        "604800, 0.0",
        "864000, 0.0",
    })
    @DisplayName("PREF-05 최신성은 기준 시각에서 7일 안이면 1 − 지난 날 수 / 7, 그 밖은 0이다")
    void freshnessFallsOverSevenDays(long secondsBefore, double freshness) {
        RecommendedProduct product = recommendedProductRegisteredAt(PRODUCT_ID, BASE_TIME.minusSeconds(secondsBefore));

        double score = ranker.scoreOf(product, PreferenceSnapshot.empty(), NO_SIMILARITIES, BASE_TIME);

        // 취향 벡터가 없는 식에서 최신성의 가중치는 0.20이다
        assertThat(score).isCloseTo(0.20 * freshness, within(1e-9));
    }

    @Test
    @DisplayName("PREF-06 취향 벡터가 있으면 0.45 × 취향 유사도 + 0.15 × 카테고리 + 0.25 × 브랜드 + 0.15 × 최신성이다")
    void scoreWithTasteVector() {
        PreferenceSnapshot snapshot = snapshot(0.6, 0.92, tasteVector());
        RecommendedProduct product = recommendedProduct(PRODUCT_ID, CATEGORY_ID, BRAND, BASE_TIME);

        double score = ranker.scoreOf(product, snapshot, Map.of(PRODUCT_ID, 0.7), BASE_TIME);

        assertThat(score).isCloseTo(0.785, within(1e-9));
    }

    @Test
    @DisplayName("PREF-06 취향 벡터가 없으면 0.45 × 카테고리 + 0.35 × 브랜드 + 0.20 × 최신성이다")
    void scoreWithoutTasteVector() {
        PreferenceSnapshot snapshot = snapshot(0.6, 0.6, null);
        RecommendedProduct product = recommendedProduct(PRODUCT_ID, CATEGORY_ID, BRAND, BASE_TIME);

        double score = ranker.scoreOf(product, snapshot, Map.of(PRODUCT_ID, 0.9), BASE_TIME);

        assertThat(score).isCloseTo(0.68, within(1e-9));
    }

    @Test
    @DisplayName("PREF-06 점수는 소수 넷째 자리로 반올림한다")
    void scoreIsRoundedToFourDecimals() {
        PreferenceSnapshot snapshot = snapshot(0.123456, 0, null);
        RecommendedProduct product = recommendedProduct(PRODUCT_ID, CATEGORY_ID, BRAND, OLD_REGISTERED_AT);

        double score = ranker.scoreOf(product, snapshot, NO_SIMILARITIES, BASE_TIME);

        // 0.45 × 0.123456 = 0.0555552
        assertThat(score).isEqualTo(0.0556);
    }

    @ParameterizedTest(name = "유사도 {0} → {1}")
    @CsvSource({
        "-0.4, 0.0",
        "0.4, 0.18",
    })
    @DisplayName("PREF-14 취향 유사도는 0 아래를 0으로 자른 값이다")
    void negativeSimilarityCountsAsZero(double similarity, double expected) {
        PreferenceSnapshot snapshot = snapshot(0, 0, tasteVector());
        RecommendedProduct product = recommendedProduct(PRODUCT_ID, CATEGORY_ID, BRAND, OLD_REGISTERED_AT);

        double score = ranker.scoreOf(product, snapshot, Map.of(PRODUCT_ID, similarity), BASE_TIME);

        assertThat(score).isCloseTo(expected, within(1e-9));
    }

    @Test
    @DisplayName("PREF-14 상품 벡터가 없는 상품은 취향 유사도 자리에 카테고리 친밀도를 쓴다")
    void missingProductVectorUsesCategoryAffinity() {
        PreferenceSnapshot snapshot = snapshot(0.6, 0, tasteVector());
        RecommendedProduct product = recommendedProduct(PRODUCT_ID, CATEGORY_ID, BRAND, OLD_REGISTERED_AT);

        double score = ranker.scoreOf(product, snapshot, NO_SIMILARITIES, BASE_TIME);

        // 0.45 × 0.6(카테고리 친밀도로 대신) + 0.15 × 0.6
        assertThat(score).isCloseTo(0.36, within(1e-9));
    }

    private static PreferenceSnapshot snapshot(double categoryScore, double brandScore, TasteVector tasteVector) {
        return new PreferenceSnapshot(
            List.of(new CategoryAffinity(CATEGORY_ID, categoryScore)),
            List.of(new BrandAffinity(BRAND, brandScore)),
            tasteVector,
            BASE_TIME.minusHours(9));
    }

    private static TasteVector tasteVector() {
        return new TasteVector(axis(0), 1.0, 1);
    }
}
