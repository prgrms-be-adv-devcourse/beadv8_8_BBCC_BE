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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest(name = "상품 유사도 {0}")
    @NullSource
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("PREF-14 상품 벡터가 없거나 유사도가 유한하지 않은 상품은 취향 유사도 자리에 카테고리 친밀도를 쓴다")
    void missingProductVectorUsesCategoryAffinity(Double similarity) {
        PreferenceSnapshot snapshot = snapshot(0.6, 0, tasteVector());
        RecommendedProduct product = recommendedProduct(PRODUCT_ID, CATEGORY_ID, BRAND, OLD_REGISTERED_AT);
        Map<Long, Double> similarities = new HashMap<>();
        similarities.put(PRODUCT_ID, similarity);

        double score = ranker.scoreOf(product, snapshot, similarities, BASE_TIME);

        // 0.45 × 0.6(카테고리 친밀도로 대신) + 0.15 × 0.6
        assertThat(score).isCloseTo(0.36, within(1e-9));
    }

    @Test
    @DisplayName("PREF-06 점수가 큰 상품이 먼저 나온다")
    void higherScoreFirst() {
        PreferenceSnapshot snapshot = snapshot(0, 0, tasteVector());
        // 점수가 낮은 상품일수록 등록 시각이 늦어, 등록 최신순이면 반대 순서가 된다
        RecommendedProduct closest = recommendedProduct(1L, CATEGORY_ID, BRAND, OLD_REGISTERED_AT);
        RecommendedProduct middle = recommendedProduct(2L, CATEGORY_ID, BRAND, OLD_REGISTERED_AT.plusDays(1));
        RecommendedProduct farthest = recommendedProduct(3L, CATEGORY_ID, BRAND, OLD_REGISTERED_AT.plusDays(2));
        Map<Long, Double> similarities = Map.of(1L, 0.9, 2L, 0.5, 3L, 0.1);

        List<RecommendedProduct> ranked =
            ranker.rank(List.of(farthest, middle, closest), snapshot, similarities, Set.of(), BASE_TIME);

        assertThat(ranked).containsExactly(closest, middle, farthest);
    }

    @Test
    @DisplayName("PREF-06 점수가 같으면 등록 시각이 늦은 상품, 그것도 같으면 상품 ID가 큰 상품이 먼저 나온다")
    void sameScoreOrderedByRegisteredAtThenProductId() {
        PreferenceSnapshot snapshot = snapshot(0.6, 0.6, null);
        // 세 상품 모두 등록한 지 7일이 넘어 카테고리 · 브랜드 몫만 받는다
        RecommendedProduct earlier = recommendedProduct(3L, CATEGORY_ID, BRAND, OLD_REGISTERED_AT);
        RecommendedProduct laterSmallerId = recommendedProduct(1L, CATEGORY_ID, BRAND, OLD_REGISTERED_AT.plusDays(1));
        RecommendedProduct laterLargerId = recommendedProduct(2L, CATEGORY_ID, BRAND, OLD_REGISTERED_AT.plusDays(1));

        List<RecommendedProduct> ranked = ranker.rank(
            List.of(earlier, laterSmallerId, laterLargerId), snapshot, NO_SIMILARITIES, Set.of(), BASE_TIME);

        assertThat(ranked).containsExactly(laterLargerId, laterSmallerId, earlier);
    }

    @Test
    @DisplayName("PREF-06 활동이 없는 자녀의 목록은 등록 최신순이다")
    void noActivityKeepsNewestFirst() {
        // 최근 7일 안에 등록한 상품과 그 전에 등록한 상품이 섞여 있다
        RecommendedProduct oldest = recommendedProductRegisteredAt(3L, OLD_REGISTERED_AT);
        RecommendedProduct older = recommendedProductRegisteredAt(1L, BASE_TIME.minusDays(10));
        RecommendedProduct recent = recommendedProductRegisteredAt(2L, BASE_TIME.minusDays(1));
        RecommendedProduct newest = recommendedProductRegisteredAt(4L, BASE_TIME);

        List<RecommendedProduct> ranked = ranker.rank(List.of(older, newest, oldest, recent),
            PreferenceSnapshot.empty(), NO_SIMILARITIES, Set.of(), BASE_TIME);

        assertThat(ranked).containsExactly(newest, recent, older, oldest);
    }

    @Test
    @DisplayName("PREF-08 장바구니에 있거나 구매한 상품처럼 제외할 상품은 목록에서 뺀다")
    void excludedProductsAreRemoved() {
        RecommendedProduct kept = recommendedProductRegisteredAt(1L, OLD_REGISTERED_AT);
        RecommendedProduct inCart = recommendedProductRegisteredAt(2L, OLD_REGISTERED_AT);
        RecommendedProduct purchased = recommendedProductRegisteredAt(3L, OLD_REGISTERED_AT);

        List<RecommendedProduct> ranked = ranker.rank(List.of(kept, inCart, purchased),
            PreferenceSnapshot.empty(), NO_SIMILARITIES, Set.of(2L, 3L), BASE_TIME);

        assertThat(ranked).containsExactly(kept);
    }

    @Test
    @DisplayName("PREF-09 기준 시각보다 늦게 등록한 상품은 빼고, 기준 시각에 등록한 상품은 남긴다")
    void productsRegisteredAfterAsOfAreRemoved() {
        RecommendedProduct atAsOf = recommendedProductRegisteredAt(1L, BASE_TIME);
        RecommendedProduct afterAsOf = recommendedProductRegisteredAt(2L, BASE_TIME.plusSeconds(1));

        List<RecommendedProduct> ranked = ranker.rank(List.of(atAsOf, afterAsOf),
            PreferenceSnapshot.empty(), NO_SIMILARITIES, Set.of(), BASE_TIME);

        assertThat(ranked).containsExactly(atAsOf);
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
