package com.bbcc.kidly.boundedcontext.recommend.domain.recommended;

/**
 * AI가 홈 맛보기로 고른 상품 하나와 그 이유다.
 */
public record HomePreviewPick(Long productId, String reason) {
}
