package com.bbcc.kidly.boundedcontext.product.app.port.product;

import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;

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
