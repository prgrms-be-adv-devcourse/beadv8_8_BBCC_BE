package com.bbcc.kidly.boundedcontext.recommend.domain.recommended;

/**
 * 추천 목록에 담기는 상품 하나다. 아이에게 맞는 사이즈를 함께 가진다.
 */
public record RecommendedProduct(
        Long productId,
        Long sellerId,
        String name,
        String thumbnailUrl,
        Long minPrice,
        String recommendedSize
) {
}
