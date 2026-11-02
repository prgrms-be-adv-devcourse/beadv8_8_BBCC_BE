package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReviewSummaryTest {

    @ParameterizedTest(name = "{0} / {1} → {2}")
    @CsvSource({"13, 3, 4.3", "14, 3, 4.7", "9, 2, 4.5", "1, 1, 1.0", "5, 1, 5.0", "7, 2, 3.5", "11, 3, 3.7"})
    @DisplayName("평균 = 별점 합 ÷ 리뷰 수, 소수 첫째 자리 HALF_UP(1.0 · 5.0도 소수 한 자리)")
    void average(long ratingSum, long reviewCount, String expected) {
        assertThat(ReviewSummary.of(reviewCount, ratingSum).averageRating()).isEqualTo(new BigDecimal(expected));
    }

    @Test
    @DisplayName("리뷰가 없으면 리뷰 수 0 · 평균 null(0점과 구분)")
    void empty() {
        ReviewSummary summary = ReviewSummary.of(0, 0);

        assertThat(summary.reviewCount()).isZero();
        assertThat(summary.averageRating()).isNull();
        assertThat(ReviewSummary.none().averageRating()).isNull();
        assertThat(ReviewSummary.none().reviewCount()).isZero();
    }
}
