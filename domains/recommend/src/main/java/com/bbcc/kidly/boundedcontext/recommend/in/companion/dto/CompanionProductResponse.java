package com.bbcc.kidly.boundedcontext.recommend.in.companion.dto;

import com.bbcc.kidly.boundedcontext.recommend.app.companion.dto.CompanionProductResult;

/**
 * 함께 사면 좋은 상품의 상품 카드 하나다.
 *
 * @param thumbnailUrl 상품에 대표 이미지가 없으면 null이다
 * @param businessName 상품을 파는 판매자의 상호를 아직 받지 못했으면 null이다
 * @param recommendedSize 사이즈 무관 상품이면 null이다
 * @param reason AI가 고른 상품이면 고른 이유이고, 규칙으로 채운 상품이면 null이다
 */
public record CompanionProductResponse(
    String productId,
    String name,
    String thumbnailUrl,
    String businessName,
    Long minPrice,
    String recommendedSize,
    String reason) {

    public static CompanionProductResponse from(CompanionProductResult result) {
        return new CompanionProductResponse(
            String.valueOf(result.productId()), result.name(), result.thumbnailUrl(),
            result.businessName(), result.minPrice(), result.recommendedSize(), result.reason());
    }
}
