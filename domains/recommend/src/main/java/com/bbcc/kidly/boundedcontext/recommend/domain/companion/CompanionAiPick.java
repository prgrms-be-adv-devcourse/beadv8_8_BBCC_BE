package com.bbcc.kidly.boundedcontext.recommend.domain.companion;

/**
 * AI 응답의 한 줄이다. AI가 고른 상품 ID와 그 상품을 고른 이유를 담는다.
 */
public record CompanionAiPick(Long productId, String reason) {
}
