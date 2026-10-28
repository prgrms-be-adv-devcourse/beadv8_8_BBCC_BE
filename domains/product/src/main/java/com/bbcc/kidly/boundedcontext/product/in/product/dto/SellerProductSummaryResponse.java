package com.bbcc.kidly.boundedcontext.product.in.product.dto;

import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductSummary;
import java.time.LocalDateTime;

/**
 * 판매자 내 상품 목록의 한 행.
 *
 * @param thumbnailUrl 없으면 null
 * @param minPrice 옵션이 없으면 null
 */
public record SellerProductSummaryResponse(
    String productId,
    String name,
    String thumbnailUrl,
    String status,
    long optionCount,
    long totalAvailableQuantity,
    Long minPrice,
    LocalDateTime createdAt) {

    public static SellerProductSummaryResponse from(SellerProductSummary summary) {
        return new SellerProductSummaryResponse(
            String.valueOf(summary.productId()),
            summary.name(),
            summary.thumbnailUrl(),
            summary.status().name(),
            summary.optionCount(),
            summary.totalAvailableQuantity(),
            summary.minPrice(),
            summary.createdAt());
    }
}
