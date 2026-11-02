package com.bbcc.kidly.boundedcontext.recommend.app.size.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 사이즈를 추천받을 상품과 자녀를 고르는 요청이다.
 *
 * @param productId 사이즈를 추천받을 상품 ID
 * @param childId 사이즈를 추천받을 자녀 ID
 */
public record SizeRecommendationRequest(
    @NotNull(message = "상품 ID가 필요합니다.") Long productId,
    @NotNull(message = "자녀 ID가 필요합니다.") Long childId) {
}
