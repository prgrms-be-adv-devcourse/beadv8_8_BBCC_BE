package com.bbcc.kidly.boundedcontext.recommend.app.size.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 사이즈 추천을 받을 자녀를 고르는 요청이다.
 *
 * @param childId 사이즈를 추천받을 자녀 ID
 */
public record SizeRecommendationRequest(@NotNull(message = "자녀 ID가 필요합니다.") Long childId) {
}
