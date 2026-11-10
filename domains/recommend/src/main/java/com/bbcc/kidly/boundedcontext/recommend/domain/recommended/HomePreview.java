package com.bbcc.kidly.boundedcontext.recommend.domain.recommended;

import java.util.List;

/**
 * 홈 화면 맛보기 결과다. 고른 방법과 상품 목록을 담는다.
 */
public record HomePreview(HomePreviewSource source, List<Item> items) {

    /**
     * 맛보기에 보여 줄 상품 하나다. 규칙으로 고르거나 빈 자리를 채운 상품, AI가 이유를 주지 않은 상품은 이유가 null이다.
     */
    public record Item(RecommendedProduct product, String reason) {
    }
}
