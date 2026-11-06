package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import java.math.BigDecimal;

/**
 * 상품 리뷰 요약.
 *
 * @param fitSummary 사이즈 핏 분포
 * @param averageRating 소수 첫째 자리까지(JSON 숫자). 리뷰가 없으면 null
 */
public record ReviewSummaryResponse(String productId, long reviewCount, BigDecimal averageRating,
                                    FitSummary fitSummary) {

    /**
     * 핏별 리뷰 수(핏을 남긴 리뷰만). 리뷰가 없으면 0 · 0 · 0.
     */
    public record FitSummary(long small, long trueToSize, long large) {
    }
}
