package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /seller/orders/{sellerOrderId}/ship. 모르는 택배사는 서비스가 400으로 막는다.
 *
 * @param carrier 택배사 (CJ · EPOST · HANJIN · LOTTE · LOGEN · DUMMY)
 */
public record ShipRequest(
    @NotBlank String carrier,
    @NotBlank @Size(max = 50) String trackingNumber) {
}
