package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import com.bbcc.kidly.shared.product.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 판매자가 상품의 판매 상태를 바꾸는 요청이다.
 *
 * @param status 바꿀 판매 상태
 */
public record ProductSaleStatusRequest(@NotNull ProductStatus status) {
}
