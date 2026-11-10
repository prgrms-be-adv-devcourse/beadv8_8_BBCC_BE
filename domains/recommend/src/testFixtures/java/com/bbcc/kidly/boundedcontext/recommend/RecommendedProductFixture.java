package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_BRAND;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;

import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import java.time.LocalDateTime;

/**
 * 추천 목록의 순서를 확인하는 테스트가 쓰는 추천 상품이다. 목록 순서에 쓰지 않는 칸은 상품 A의 값을 그대로 쓴다.
 */
public final class RecommendedProductFixture {

    /**
     * 점수를 매기는 기준 시각이다.
     */
    public static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 6, 12, 0);

    /**
     * 기준 시각보다 30일 이른 등록 시각이다. 최신성 몫을 받지 않는다.
     */
    public static final LocalDateTime OLD_REGISTERED_AT = BASE_TIME.minusDays(30);

    private RecommendedProductFixture() {
    }

    public static RecommendedProduct recommendedProduct(
        Long productId, Long categoryId, String brand, LocalDateTime registeredAt) {
        return new RecommendedProduct(productId, PRODUCT_A_SELLER_ID, PRODUCT_A_NAME, PRODUCT_A_THUMBNAIL_URL,
            25_000L, "110", categoryId, brand, registeredAt);
    }

    /**
     * 상품 A의 카테고리 · 브랜드로 상품 ID와 등록 시각만 바꾼다.
     */
    public static RecommendedProduct recommendedProductRegisteredAt(Long productId, LocalDateTime registeredAt) {
        return recommendedProduct(productId, PRODUCT_A_CATEGORY_ID, PRODUCT_A_BRAND, registeredAt);
    }
}
