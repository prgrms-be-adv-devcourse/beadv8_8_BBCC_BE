package com.bbcc.kidly.boundedcontext.review.app;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 상품 리뷰 요약. 평균 별점 = 별점 합 ÷ 리뷰 수, 소수 첫째 자리까지 반올림(HALF_UP, 1.0 · 5.0도 한 자리).
 * 리뷰가 없으면 평균은 null이다(0점과 구분).
 *
 * @param averageRating 리뷰가 없으면 null
 */
public record ReviewSummary(long reviewCount, BigDecimal averageRating) {

    private static final int SCALE = 1;

    public static ReviewSummary of(long reviewCount, long ratingSum) {
        if (reviewCount <= 0) {
            return none();
        }
        return new ReviewSummary(reviewCount,
            BigDecimal.valueOf(ratingSum).divide(BigDecimal.valueOf(reviewCount), SCALE, RoundingMode.HALF_UP));
    }

    public static ReviewSummary none() {
        return new ReviewSummary(0, null);
    }
}
