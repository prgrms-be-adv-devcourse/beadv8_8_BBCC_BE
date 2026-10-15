package com.bbcc.kidly.boundedcontext.product.in.product.dto;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductSaleStatusResult;

public record ProductSaleStatusResponse(String productId, String status, Long version) {

    public static ProductSaleStatusResponse from(ProductSaleStatusResult result) {
        return new ProductSaleStatusResponse(
            String.valueOf(result.productId()), result.status().name(), result.version());
    }
}
