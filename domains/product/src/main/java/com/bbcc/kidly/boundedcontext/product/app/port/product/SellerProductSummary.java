package com.bbcc.kidly.boundedcontext.product.app.port.product;

import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;

/**
 * 판매자 내 상품 목록의 한 행. 보관 옵션은 옵션 수 · 재고 합계 · 최저가에서 뺀다.
 *
 * @param thumbnailUrl 없으면 null
 * @param totalAvailableQuantity 옵션들의 판매 가능 수량 합계
 * @param soldOutOptionCount 판매중 옵션 가운데 판매 가능 수량이 0인 옵션 수. 판매중지 · 보관 옵션은 세지 않는다
 * @param lowStockOptionCount 판매중 옵션 가운데 판매 가능 수량이 1 이상 기준 수 이하인 옵션 수
 * @param minPrice 옵션 판매가 중 가장 낮은 값. 옵션이 없으면 null
 */
public record SellerProductSummary(
    Long productId,
    String name,
    String thumbnailUrl,
    ProductStatus status,
    long optionCount,
    long totalAvailableQuantity,
    long soldOutOptionCount,
    long lowStockOptionCount,
    Long minPrice,
    LocalDateTime createdAt) {
}
