package com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto;

import java.util.List;

/**
 * 홈 맛보기 결과다. 상품을 고른 방법과 보여 줄 상품 목록을 담는다.
 *
 * @param source 상품을 고른 방법의 이름이다. AI가 골랐으면 AI, 규칙으로 골랐으면 RULE이다
 */
public record HomePreviewResult(String source, List<Item> items) {

    /**
     * 맛보기에 보여 줄 상품 하나다.
     *
     * @param reason 이 상품을 추천하는 이유다. 이유가 없으면 null이다
     */
    public record Item(RecommendedProductResult product, String reason) {
    }
}
