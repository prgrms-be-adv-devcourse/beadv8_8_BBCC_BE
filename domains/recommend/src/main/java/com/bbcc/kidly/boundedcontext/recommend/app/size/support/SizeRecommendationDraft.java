package com.bbcc.kidly.boundedcontext.recommend.app.size.support;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeAiEvidence;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.WearPreview;

/**
 * 트랜잭션 안에서 읽은 값으로 만든 사이즈 추천의 초안이다. 규칙 결과와, 트랜잭션 밖에서 AI를 부르는 데 필요한 값을 담는다.
 *
 * @param rule 규칙으로 계산한 결과
 * @param aiKeyInput 저장된 AI 답을 찾는 키를 만드는 입력
 * @param aiQuestion AI에게 보내는 질문
 * @param aiEvidence AI 답을 검증할 때 쓰는, AI에게 준 값
 * @param wearPreview 자녀가 상품 사이즈를 입은 모습의 착용 미리보기이고, 만들지 않는 상품이면 null이다
 * @param lastPurchasedSize 같은 칸에서 마지막으로 구매확정한 사이즈 이름이고, 구매가 없으면 null이다
 */
public record SizeRecommendationDraft(
    Long productId,
    Long childId,
    ProductSizes productSizes,
    SizeRecommendation rule,
    AiResponseKey.Input aiKeyInput,
    SizeAiQuestion aiQuestion,
    SizeAiEvidence aiEvidence,
    WearPreview wearPreview,
    String lastPurchasedSize) {
}
