package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignalType;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    private static PreferenceSignal signal(
        PreferenceSignalType type, LocalDateTime occurredAt, Long categoryId, String brand, Long productId) {
        return new PreferenceSignal(type, occurredAt, categoryId, brand, productId, false);
    }
}
