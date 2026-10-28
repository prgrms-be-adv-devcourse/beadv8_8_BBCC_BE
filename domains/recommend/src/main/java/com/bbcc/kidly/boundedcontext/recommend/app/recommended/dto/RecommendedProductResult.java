package com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto;

/**
 * 자녀에게 추천하는 상품 한 건이다. 사이즈 무관 상품이면 추천 사이즈는 null이다.
 *
 * @param minPrice 판매 가능한 옵션 중 가장 낮은 판매가다
 * @param recommendedSize 자녀에게 추천하는 사이즈다
 */
public record RecommendedProductResult(
    Long productId,
    String name,
    String thumbnailUrl,
    String businessName,
    Long minPrice,
    String recommendedSize) {
}
