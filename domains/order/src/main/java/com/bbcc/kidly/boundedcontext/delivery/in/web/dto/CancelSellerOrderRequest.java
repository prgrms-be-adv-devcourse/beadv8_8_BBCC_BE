package com.bbcc.kidly.boundedcontext.delivery.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /seller/orders/{sellerOrderId}/cancel(#655). 모르는 사유는 서비스가 400으로 막는다.
 *
 * @param reason 취소 사유 (OUT_OF_STOCK · WRONG_PRODUCT_INFO · OTHER)
 * @param memo 판매자 메모(선택, 500자 이하). 이벤트 · 로그에는 싣지 않는다
 */
public record CancelSellerOrderRequest(
    @NotBlank String reason,
    @Size(max = 500) String memo) {
}
