package com.bbcc.kidly.boundedcontext.recommend.domain.recommended;

import java.util.List;

/**
 * 홈 화면 맛보기 결과다. 고른 방법과 상품 목록을 담는다.
 */
public record HomePreview(HomePreviewSource source, List<Item> items) {

    /**
     * 맛보기에 보여 줄 상품 하나다. 규칙으로 고른 상품은 이유가 없어 null이다.
     */
    public record Item(RecommendedProduct product, String reason) {
    }
}
