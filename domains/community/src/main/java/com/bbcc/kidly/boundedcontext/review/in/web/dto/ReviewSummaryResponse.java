package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import java.math.BigDecimal;

/**
 * 상품 리뷰 요약.
 *
 * @param averageRating 소수 첫째 자리까지(JSON 숫자). 리뷰가 없으면 null
 */
public record ReviewSummaryResponse(String productId, long reviewCount, BigDecimal averageRating) {
}
