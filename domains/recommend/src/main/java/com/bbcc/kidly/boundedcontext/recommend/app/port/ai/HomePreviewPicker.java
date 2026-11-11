package com.bbcc.kidly.boundedcontext.recommend.app.port.ai;

/**
 * 홈 맛보기 후보 중에서 자녀에게 보여 줄 상품을 AI에게 고르게 하는 요청서다.
 */
public interface HomePreviewPicker {

    HomePreviewAiAnswer pick(HomePreviewAiRequest request);
}
