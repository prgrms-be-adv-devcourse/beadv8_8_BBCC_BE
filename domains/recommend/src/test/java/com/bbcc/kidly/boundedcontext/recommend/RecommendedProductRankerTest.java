package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.BASE_TIME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.OLD_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.recommendedProduct;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.recommendedProductRegisteredAt;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceActivity;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProductRanker;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecommendedProductRankerTest {

    private static final Long CATEGORY_ID = 52L;

    private static final String BRAND = "키들리";

    private final RecommendedProductRanker ranker = new RecommendedProductRanker();

    @ParameterizedTest(name = "등록 시각 {0} → {1}점")
    @CsvSource({
        "2026-10-30T12:00:00, 1",
        "2026-10-30T11:59:59, 0",
        "2026-11-06T12:00:00, 1",
    })
    @DisplayName("PREF-05 기준 시각 7일 전부터 기준 시각까지 등록한 상품은 1점, 그보다 이르면 0점이다")
    void recentlyRegisteredProductGetsOnePoint(LocalDateTime registeredAt, int expected) {
        RecommendedProduct product = recommendedProductRegisteredAt(1L, registeredAt);

        assertThat(ranker.scoreOf(product, ChildPreference.empty(), BASE_TIME)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "담기 {0}, 상품 카테고리 {1}, 브랜드 {2}, 최근 등록 {3} → {4}점")
    @CsvSource({
        "true, 52, 키들리, true, 8",
        "true, 52, 키들리, false, 7",
        "false, 52, 키들리, true, 6",
        "false, 52, 다른브랜드, false, 3",
        "true, 99, 키들리, false, 2",
        "false, 99, 다른브랜드, false, 0",
    })
    @DisplayName("PREF-02 ~ 05 점수는 카테고리 · 담기 · 브랜드 · 최신성 몫을 더한 값이다")
    void scoreIsSumOfAllPoints(boolean cartAdd, Long productCategoryId, String productBrand, boolean recent,
        int expected) {
        ChildPreference preference = ChildPreference.empty().plus(new PreferenceActivity(CATEGORY_ID, BRAND, cartAdd));
        LocalDateTime registeredAt = recent ? BASE_TIME : OLD_REGISTERED_AT;
        RecommendedProduct product = recommendedProduct(1L, productCategoryId, productBrand, registeredAt);

        assertThat(ranker.scoreOf(product, preference, BASE_TIME)).isEqualTo(expected);
    }
}
