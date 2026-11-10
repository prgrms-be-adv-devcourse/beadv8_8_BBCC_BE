package com.bbcc.kidly.boundedcontext.recommend.domain.recommended;

import java.time.LocalDateTime;

/**
 * 추천 목록에 담기는 상품 하나다. 아이에게 맞는 사이즈를 함께 가진다.
 * 카테고리 · 브랜드 · 등록 시각 · 판매 시작 시각은 자녀의 선호 점수로 목록 순서를 정할 때 쓴다.
 */
public record RecommendedProduct(
        Long productId,
        Long sellerId,
        String name,
        String thumbnailUrl,
        Long minPrice,
        String recommendedSize,
        Long categoryId,
        String brand,
        LocalDateTime productRegisteredAt,
        LocalDateTime saleStartedAt
) {

    // 판매 시작 시각을 따로 알 수 없으면 등록 시각을 판매 시작 시각으로 본다
    public RecommendedProduct(
            Long productId,
            Long sellerId,
            String name,
            String thumbnailUrl,
            Long minPrice,
            String recommendedSize,
            Long categoryId,
            String brand,
            LocalDateTime productRegisteredAt
    ) {
        this(productId, sellerId, name, thumbnailUrl, minPrice, recommendedSize, categoryId, brand,
                productRegisteredAt, productRegisteredAt);
    }
}
