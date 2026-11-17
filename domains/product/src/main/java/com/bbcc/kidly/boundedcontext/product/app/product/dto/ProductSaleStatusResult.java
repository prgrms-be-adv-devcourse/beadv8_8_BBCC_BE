package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import com.bbcc.kidly.shared.product.domain.ProductStatus;

public record ProductSaleStatusResult(Long productId, ProductStatus status, Long version) {
}
