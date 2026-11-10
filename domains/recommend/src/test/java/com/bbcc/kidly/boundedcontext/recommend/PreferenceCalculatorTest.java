package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignalType;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import java.time.LocalDateTime;
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

    private static PreferenceSignal signal(
        PreferenceSignalType type, LocalDateTime occurredAt, Long categoryId, String brand, Long productId) {
        return new PreferenceSignal(type, occurredAt, categoryId, brand, productId, false);
    }
}
