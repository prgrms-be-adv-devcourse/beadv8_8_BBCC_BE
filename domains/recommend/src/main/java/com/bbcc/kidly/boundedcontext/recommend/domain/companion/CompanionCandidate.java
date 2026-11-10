package com.bbcc.kidly.boundedcontext.recommend.domain.companion;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import java.util.List;
import java.util.Optional;

/**
 * 함께 사면 좋은 상품의 후보 하나다. 보고 있는 상품과의 유사도는 임베딩 벡터가 없으면 null이다.
 */
public record CompanionCandidate(RecommendedProduct product, List<String> categoryPathNames, Double similarity) {

    public CompanionCandidate {
        requireNonNull(product, "product는 필수입니다");
        categoryPathNames = List.copyOf(requireNonNull(categoryPathNames, "categoryPathNames는 필수입니다"));
    }

    public Optional<CompanionKind> kind() {
        return CompanionKind.of(categoryPathNames);
    }
}
