package com.bbcc.kidly.boundedcontext.recommend.domain.ai;

/**
 * 지난 AI 답을 다시 쓰는 기능의 종류다.
 */
public enum AiResponseType {

    /** 홈 맛보기 AI 답이다. */
    HOME_PREVIEW,

    /** 함께 사면 좋은 상품 답이다. */
    BOUGHT_TOGETHER,

    /** AI 사이즈 추천 답이다. */
    SIZE_RECOMMENDATION
}
