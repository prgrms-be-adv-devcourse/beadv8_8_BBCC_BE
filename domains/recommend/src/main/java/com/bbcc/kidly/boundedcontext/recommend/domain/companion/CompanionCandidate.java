package com.bbcc.kidly.boundedcontext.recommend.domain.companion;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;

/**
 * 함께 사면 좋은 상품의 후보 하나다. 보고 있는 상품과의 유사도는 임베딩 벡터가 없으면 null이다.
 */
public record CompanionCandidate(RecommendedProduct product, CompanionKind kind, Double similarity) {

    public CompanionCandidate {
        requireNonNull(product, "product는 필수입니다");
        requireNonNull(kind, "kind는 필수입니다");
    }
}
