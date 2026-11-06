package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.BASE_TIME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.recommendedProductRegisteredAt;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProductRanker;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecommendedProductRankerTest {

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
}
