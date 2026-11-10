package com.bbcc.kidly.boundedcontext.recommend.app.port.ai;

import java.util.List;

/**
 * AI가 홈 맛보기로 고른 상품과 이유다. AI 응답 저장소에 이 모양 그대로 JSON으로 남는다.
 * 그래서 칸 이름이나 뜻을 바꾸면 프롬프트 버전을 올려야 하고, 도메인의 {@code HomePreviewPick}과 따로 둔다.
 *
 * @param picks 고른 상품이다. AI가 답하지 못한 칸은 null일 수 있다
 */
public record HomePreviewAiAnswer(List<Pick> picks) {

    /**
     * @param reason 고른 이유다. 없으면 null이다
     */
    public record Pick(Long productId, String reason) {
    }
}
