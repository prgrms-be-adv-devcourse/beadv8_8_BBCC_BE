package com.bbcc.kidly.boundedcontext.recommend.in.recommended.dto;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.HomePreviewResult;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.RecommendedProductResult;
import java.util.ArrayList;
import java.util.List;

/**
 * 홈 화면 맛보기 응답이다.
 *
 * @param source 상품을 고른 방법이다. AI가 골랐으면 AI, 규칙으로 골랐으면 RULE이다
 * @param products 보여 줄 상품이다. 자녀에게 맞는 상품이 없으면 빈 목록이다
 */
public record HomePreviewResponse(String source, List<Product> products) {

    public static HomePreviewResponse from(HomePreviewResult result) {
        List<Product> products = new ArrayList<>();
        for (HomePreviewResult.Item item : result.items()) {
            products.add(Product.from(item));
        }
        return new HomePreviewResponse(result.source(), products);
    }

    /**
     * 맛보기 상품 카드 하나다. 카드 칸은 더보기 목록의 카드와 같다.
     *
     * @param reason 이 상품을 추천하는 이유다. 이유가 없으면 null이다
     */
    public record Product(
        String productId,
        String name,
        String thumbnailUrl,
        String businessName,
        Long minPrice,
        String recommendedSize,
        String reason) {

        static Product from(HomePreviewResult.Item item) {
            RecommendedProductResult card = item.product();
            return new Product(
                String.valueOf(card.productId()), card.name(), card.thumbnailUrl(),
                card.businessName(), card.minPrice(), card.recommendedSize(), item.reason());
        }
    }
}
