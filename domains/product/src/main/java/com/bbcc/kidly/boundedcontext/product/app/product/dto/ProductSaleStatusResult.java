package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import com.bbcc.kidly.shared.product.domain.ProductStatus;

/**
 * 판매 상태를 바꾼 뒤의 상품이다.
 */
public record ProductSaleStatusResult(Long productId, ProductStatus status, Long version) {
}
