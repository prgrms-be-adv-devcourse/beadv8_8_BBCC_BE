package com.bbcc.kidly.boundedcontext.product.app.port.product;

import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;

/**
 * 판매자 내 상품 목록의 한 행. 보관 옵션은 옵션 수 · 재고 합계 · 최저가에서 뺀다.
 *
 * @param thumbnailUrl 없으면 null
 * @param totalAvailableQuantity 옵션들의 판매 가능 수량 합계
 * @param minPrice 옵션 판매가 중 가장 낮은 값. 옵션이 없으면 null
 */
public record SellerProductSummary(
    Long productId,
    String name,
    String thumbnailUrl,
    ProductStatus status,
    long optionCount,
    long totalAvailableQuantity,
    Long minPrice,
    LocalDateTime createdAt) {
}
