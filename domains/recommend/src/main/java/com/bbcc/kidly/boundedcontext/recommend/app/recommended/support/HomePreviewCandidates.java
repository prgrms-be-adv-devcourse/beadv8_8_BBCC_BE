package com.bbcc.kidly.boundedcontext.recommend.app.recommended.support;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import java.util.List;

/**
 * 홈 맛보기 후보와, 트랜잭션 안에서 미리 채워 둔 AI 요청이다.
 * AI 호출은 트랜잭션 밖 다른 스레드에서 돌므로 요청에 쓸 값을 모두 담아 둔다.
 *
 * @param childProfileId AI 응답을 다시 쓰는 키에 쓰는 자녀 ID다. AI에 보내는 값이 아니다
 * @param products 선호 점수 순서의 후보다
 */
public record HomePreviewCandidates(
    Long childProfileId, List<RecommendedProduct> products, HomePreviewAiRequest aiRequest) {
}
