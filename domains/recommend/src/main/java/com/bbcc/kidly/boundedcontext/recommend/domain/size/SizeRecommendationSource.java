package com.bbcc.kidly.boundedcontext.recommend.domain.size;

/**
 * 사이즈 추천 답을 누가 골랐는지다. 근거가 비었는지로 추정하면 근거 없이 답한 AI와 규칙을 구분하지 못해 따로 둔다.
 */
public enum SizeRecommendationSource {
    AI,
    RULE
}
