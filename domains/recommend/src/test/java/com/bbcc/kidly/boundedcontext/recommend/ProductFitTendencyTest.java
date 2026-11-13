package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductFitTendency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 공개된 핏 리뷰 수로 상품 핏 성향을 정하는 규칙을 확인한다.
 */
class ProductFitTendencyTest {

    @ParameterizedTest
    @CsvSource({
        "0, 0, 0",
        "1, 0, 0",
        "1, 2, 1",
        "0, 0, 4"
    })
    @DisplayName("FIT-13 공개된 핏 리뷰가 5건보다 적으면 한 사람 의견이 성향처럼 보이지 않게 상품 핏 성향을 만들지 않는다")
    void noTendencyBelowFiveReviews(int small, int trueToSize, int large) {
        assertThat(ProductFitTendency.ofCounts(small, trueToSize, large)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "2, 2, 1",
        "0, 5, 0",
        "3, 4, 6"
    })
    @DisplayName("FIT-13 공개된 핏 리뷰가 5건 이상이면 작아요 · 잘 맞아요 · 커요 수를 그대로 상품 핏 성향으로 쓴다")
    void tendencyFromFiveReviews(int small, int trueToSize, int large) {
        assertThat(ProductFitTendency.ofCounts(small, trueToSize, large))
            .isEqualTo(new ProductFitTendency(small, trueToSize, large));
    }
}
