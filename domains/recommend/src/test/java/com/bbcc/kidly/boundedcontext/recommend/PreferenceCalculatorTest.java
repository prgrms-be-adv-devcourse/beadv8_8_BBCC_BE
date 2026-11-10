package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static com.bbcc.kidly.boundedcontext.recommend.EmbeddingVectorFixture.axes;
import static com.bbcc.kidly.boundedcontext.recommend.EmbeddingVectorFixture.axis;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignalType;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.TasteVector;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PreferenceCalculatorTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 10, 3, 0);

    private static final Long CATEGORY_ID = 52L;

    private static final String BRAND = "키들리";

    private static final Map<Long, EmbeddingVector> NO_EMBEDDINGS = Map.of();

    private final PreferenceCalculator calculator = new PreferenceCalculator();

    @ParameterizedTest(name = "{0} {1}일 전 → {2}")
    @CsvSource({
        "PRODUCT_VIEWED, 0, 0.3",
        "SEARCH, 0, 0.3",
        "CART_ITEM_ADDED, 0, 0.6",
        "CART_ITEM_ADDED, 15, 0.3",
    })
    @DisplayName("PREF-02 활동 하나의 무게는 강도(상세 0.3 · 검색 0.3 · 담기 0.6) × 최근성(1 − 지난 날 수 / 30)이다")
    void weightIsIntensityTimesRecency(PreferenceSignalType type, long daysAgo, double expected) {
        PreferenceSignal signal = signal(type, BASE_TIME.minusDays(daysAgo), CATEGORY_ID, BRAND, 1L);

        PreferenceSnapshot snapshot = calculator.calculate(List.of(signal), NO_EMBEDDINGS, BASE_TIME);

        assertThat(snapshot.categoryAffinity(CATEGORY_ID)).isCloseTo(expected, within(1e-9));
    }

    @ParameterizedTest(name = "기준 시각 {0}초 전 상세 진입 → {1}")
    @CsvSource({
        "0, 0.3",
        "1296000, 0.15",
        "2592000, 0.0",
        "2592001, 0.0",
        "-1, 0.0",
    })
    @DisplayName("PREF-01 기준 시각 30일 전부터 기준 시각까지 일어난 활동만 센다")
    void onlyActivitiesWithinThirtyDaysCount(long secondsBefore, double expected) {
        PreferenceSignal signal =
            signal(PreferenceSignalType.PRODUCT_VIEWED, BASE_TIME.minusSeconds(secondsBefore), CATEGORY_ID, BRAND, 1L);

        PreferenceSnapshot snapshot = calculator.calculate(List.of(signal), NO_EMBEDDINGS, BASE_TIME);

        assertThat(snapshot.categoryAffinity(CATEGORY_ID)).isCloseTo(expected, within(1e-9));
    }

    @ParameterizedTest(name = "오늘 상세 {0}번 · 담기 {1}번 → {2}")
    @CsvSource({
        "3, 0, 0.657",
        "1, 1, 0.72",
        "0, 0, 0.0",
    })
    @DisplayName("PREF-03 카테고리 친밀도는 그 카테고리가 나온 활동 무게로 1 − Π(1 − 무게)이고, 활동이 없으면 0이다")
    void categoryAffinityCombinesWeights(int views, int cartAdds, double expected) {
        List<PreferenceSignal> signals = new ArrayList<>();
        for (int i = 0; i < views; i++) {
            signals.add(signal(PreferenceSignalType.PRODUCT_VIEWED, BASE_TIME, CATEGORY_ID, BRAND, 1L));
        }
        for (int i = 0; i < cartAdds; i++) {
            signals.add(signal(PreferenceSignalType.CART_ITEM_ADDED, BASE_TIME, CATEGORY_ID, BRAND, 1L));
        }

        PreferenceSnapshot snapshot = calculator.calculate(signals, NO_EMBEDDINGS, BASE_TIME);

        assertThat(snapshot.categoryAffinity(CATEGORY_ID)).isCloseTo(expected, within(1e-9));
    }

    @Test
    @DisplayName("PREF-03 카테고리가 없는 활동은 어느 카테고리에도 넣지 않는다")
    void activityWithoutCategoryIsLeftOut() {
        PreferenceSignal signal = signal(PreferenceSignalType.SEARCH, BASE_TIME, null, null, null);

        PreferenceSnapshot snapshot = calculator.calculate(List.of(signal), NO_EMBEDDINGS, BASE_TIME);

        assertThat(snapshot.categories()).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" 브랜드 → {1}")
    @CsvSource({
        "키들리, 0.3",
        "'키들리 ', 0.0",
        "키들리키즈, 0.0",
    })
    @DisplayName("PREF-04 브랜드 친밀도는 브랜드 글자가 같을 때만 일치한다")
    void brandMatchesOnlySameText(String brand, double expected) {
        PreferenceSignal signal = signal(PreferenceSignalType.PRODUCT_VIEWED, BASE_TIME, CATEGORY_ID, BRAND, 1L);

        PreferenceSnapshot snapshot = calculator.calculate(List.of(signal), NO_EMBEDDINGS, BASE_TIME);

        assertThat(snapshot.brandAffinity(brand)).isCloseTo(expected, within(1e-9));
    }

    @ParameterizedTest(name = "브랜드 \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("PREF-04 브랜드가 없는 활동은 어느 브랜드에도 넣지 않는다")
    void activityWithoutBrandIsLeftOut(String brand) {
        PreferenceSignal signal = signal(PreferenceSignalType.CART_ITEM_ADDED, BASE_TIME, CATEGORY_ID, brand, 1L);

        PreferenceSnapshot snapshot = calculator.calculate(List.of(signal), NO_EMBEDDINGS, BASE_TIME);

        assertThat(snapshot.brands()).isEmpty();
    }

    @ParameterizedTest(name = "{0}일 전 구매확정 → {1}")
    @CsvSource({
        "0, 0.8",
        "90, 0.4",
        "181, 0.0",
    })
    @DisplayName("PREF-16 구매확정의 무게는 0.8 × (1 − 지난 날 수 / 180)이고 180일 안의 것만 센다")
    void purchaseConfirmedWeight(long daysAgo, double expected) {
        PreferenceSignal signal =
            signal(PreferenceSignalType.PURCHASE_CONFIRMED, BASE_TIME.minusDays(daysAgo), CATEGORY_ID, BRAND, 1L);

        PreferenceSnapshot snapshot = calculator.calculate(List.of(signal), NO_EMBEDDINGS, BASE_TIME);

        assertThat(snapshot.brandAffinity(BRAND)).isCloseTo(expected, within(1e-9));
    }

    @Test
    @DisplayName("PREF-16 구매확정은 카테고리 친밀도와 취향 벡터에 넣지 않는다")
    void purchaseConfirmedCountsOnlyForBrand() {
        PreferenceSignal signal = signal(PreferenceSignalType.PURCHASE_CONFIRMED, BASE_TIME, CATEGORY_ID, BRAND, 1L);

        PreferenceSnapshot snapshot =
            calculator.calculate(List.of(signal), Map.of(1L, axis(0)), BASE_TIME);

        assertThat(snapshot.categoryAffinity(CATEGORY_ID)).isZero();
        assertThat(snapshot.tasteVector()).isNull();
    }

    @Test
    @DisplayName("PREF-16 같은 브랜드를 오늘 구매확정하고 오늘 담기도 했으면 브랜드 친밀도는 1 − 0.2 × 0.4 = 0.92다")
    void purchaseConfirmedCombinesWithCartAdd() {
        List<PreferenceSignal> signals = List.of(
            signal(PreferenceSignalType.PURCHASE_CONFIRMED, BASE_TIME, CATEGORY_ID, BRAND, 1L),
            signal(PreferenceSignalType.CART_ITEM_ADDED, BASE_TIME, CATEGORY_ID, BRAND, 2L));

        PreferenceSnapshot snapshot = calculator.calculate(signals, NO_EMBEDDINGS, BASE_TIME);

        assertThat(snapshot.brandAffinity(BRAND)).isCloseTo(0.92, within(1e-9));
    }

    @Test
    @DisplayName("PREF-07 장바구니에서 뺀 담기는 무게 0이고, 같은 카테고리 · 브랜드의 다른 활동은 그대로 센다")
    void removedCartAddWeighsNothing() {
        List<PreferenceSignal> signals = List.of(
            removedCartAdd(BASE_TIME, CATEGORY_ID, BRAND, 1L),
            signal(PreferenceSignalType.PRODUCT_VIEWED, BASE_TIME, CATEGORY_ID, BRAND, 2L));
        Map<Long, EmbeddingVector> embeddings = Map.of(1L, axis(0), 2L, axis(1));

        PreferenceSnapshot snapshot = calculator.calculate(signals, embeddings, BASE_TIME);

        assertThat(snapshot.categoryAffinity(CATEGORY_ID)).isCloseTo(0.3, within(1e-9));
        assertThat(snapshot.brandAffinity(BRAND)).isCloseTo(0.3, within(1e-9));
        assertThat(snapshot.tasteVector().embedding()).isEqualTo(axis(1));
        assertThat(snapshot.tasteVector().activityCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("PREF-13 취향 벡터는 상세 진입 · 담기마다 무게 × 상품 벡터를 더해 길이를 1로 맞춘 것이다")
    void tasteVectorIsNormalizedWeightedSum() {
        List<PreferenceSignal> signals = List.of(
            signal(PreferenceSignalType.CART_ITEM_ADDED, BASE_TIME, CATEGORY_ID, BRAND, 1L),
            signal(PreferenceSignalType.PRODUCT_VIEWED, BASE_TIME, CATEGORY_ID, BRAND, 2L));
        Map<Long, EmbeddingVector> embeddings = Map.of(1L, axis(0), 2L, axis(1));

        TasteVector tasteVector = calculator.calculate(signals, embeddings, BASE_TIME).tasteVector();

        // 0.6 × 축0 + 0.3 × 축1을 길이 1로 맞추면 (2, 1) / √5다
        float[] values = tasteVector.embedding().values();
        assertThat((double) values[0]).isCloseTo(2 / Math.sqrt(5), within(1e-6));
        assertThat((double) values[1]).isCloseTo(1 / Math.sqrt(5), within(1e-6));
        assertThat(tasteVector.weightSum()).isCloseTo(0.9, within(1e-9));
        assertThat(tasteVector.activityCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("PREF-13 상품 벡터가 없는 활동과 검색은 취향 벡터에 넣지 않는다")
    void tasteVectorSkipsMissingEmbeddingAndSearch() {
        List<PreferenceSignal> signals = List.of(
            signal(PreferenceSignalType.PRODUCT_VIEWED, BASE_TIME, CATEGORY_ID, BRAND, 1L),
            signal(PreferenceSignalType.CART_ITEM_ADDED, BASE_TIME, CATEGORY_ID, BRAND, 3L),
            signal(PreferenceSignalType.SEARCH, BASE_TIME, CATEGORY_ID, null, 2L));
        Map<Long, EmbeddingVector> embeddings = Map.of(1L, axis(0), 2L, axis(1));

        TasteVector tasteVector = calculator.calculate(signals, embeddings, BASE_TIME).tasteVector();

        assertThat(tasteVector.embedding()).isEqualTo(axis(0));
        assertThat(tasteVector.weightSum()).isCloseTo(0.3, within(1e-9));
        assertThat(tasteVector.activityCount()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("signalsWithoutTasteVector")
    @DisplayName("PREF-13 넣을 활동이 없거나 합이 0 벡터면 취향 벡터는 없다")
    void noTasteVectorWithoutUsableActivity(String description, List<PreferenceSignal> signals) {
        Map<Long, EmbeddingVector> embeddings = Map.of(1L, axis(0), 2L, axes(0, -1, 0, 0));

        PreferenceSnapshot snapshot = calculator.calculate(signals, embeddings, BASE_TIME);

        assertThat(snapshot.tasteVector()).isNull();
    }

    static Stream<Arguments> signalsWithoutTasteVector() {
        return Stream.of(
            Arguments.of("활동 없음", List.of()),
            Arguments.of("벡터 없는 상품만 봄",
                List.of(signal(PreferenceSignalType.PRODUCT_VIEWED, BASE_TIME, CATEGORY_ID, BRAND, 3L))),
            Arguments.of("서로 반대인 벡터를 같은 무게로 봄", List.of(
                signal(PreferenceSignalType.PRODUCT_VIEWED, BASE_TIME, CATEGORY_ID, BRAND, 1L),
                signal(PreferenceSignalType.PRODUCT_VIEWED, BASE_TIME, CATEGORY_ID, BRAND, 2L))));
    }

    private static PreferenceSignal removedCartAdd(
        LocalDateTime occurredAt, Long categoryId, String brand, Long productId) {
        return new PreferenceSignal(PreferenceSignalType.CART_ITEM_ADDED, occurredAt, categoryId, brand, productId, true);
    }

    private static PreferenceSignal signal(
        PreferenceSignalType type, LocalDateTime occurredAt, Long categoryId, String brand, Long productId) {
        return new PreferenceSignal(type, occurredAt, categoryId, brand, productId, false);
    }
}
