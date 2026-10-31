package com.bbcc.kidly.boundedcontext.delivery.in.web.dto;

import com.bbcc.kidly.boundedcontext.delivery.app.dto.DeliveryResult;
import java.time.LocalDateTime;

/**
 * 발송 · 배송 완료 응답. ID는 문자열.
 */
public record DeliveryResponse(
    String sellerOrderId,
    String orderId,
    String status,
    String carrier,
    String trackingNumber,
    LocalDateTime shippedAt,
    LocalDateTime deliveredAt) {

    public static DeliveryResponse from(DeliveryResult result) {
        return new DeliveryResponse(
            String.valueOf(result.deliveryId()),
            String.valueOf(result.orderId()),
            result.status(),
            result.carrier(),
            result.trackingNumber(),
            result.shippedAt(),
            result.deliveredAt());
    }
}
