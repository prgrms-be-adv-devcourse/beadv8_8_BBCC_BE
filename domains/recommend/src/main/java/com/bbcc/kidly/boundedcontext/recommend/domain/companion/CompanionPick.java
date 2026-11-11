package com.bbcc.kidly.boundedcontext.recommend.domain.companion;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;

/**
 * 함께 사면 좋은 상품으로 고른 상품 하나다. 이유는 AI가 고른 자리에만 있고, 규칙으로 채운 자리는 null이다.
 */
public record CompanionPick(RecommendedProduct product, String reason) {

    public CompanionPick {
        requireNonNull(product, "product는 필수입니다");
    }
}
