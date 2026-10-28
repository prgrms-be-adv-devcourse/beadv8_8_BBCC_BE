package com.bbcc.kidly.boundedcontext.recommend.in.recommended.dto;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.RecommendedProductResult;

/**
 * 자녀 맞춤 상품 목록의 상품 카드 하나다.
 *
 * @param thumbnailUrl 상품에 대표 이미지가 없으면 null이다
 * @param businessName 상품을 파는 판매자의 상호를 아직 받지 못했으면 null이다
 * @param recommendedSize 사이즈 무관 상품이면 null이다
 */
public record RecommendedProductResponse(
    String productId,
    String name,
    String thumbnailUrl,
    String businessName,
    Long minPrice,
    String recommendedSize) {

    public static RecommendedProductResponse from(RecommendedProductResult result) {
        return new RecommendedProductResponse(
            String.valueOf(result.productId()), result.name(), result.thumbnailUrl(),
            result.businessName(), result.minPrice(), result.recommendedSize());
    }
}
