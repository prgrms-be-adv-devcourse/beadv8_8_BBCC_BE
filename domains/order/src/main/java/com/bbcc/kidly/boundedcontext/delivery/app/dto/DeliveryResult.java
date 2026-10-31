package com.bbcc.kidly.boundedcontext.delivery.app.dto;

import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import java.time.LocalDateTime;

/**
 * 발송 · 배송 완료 결과. 상태 · 택배사는 enum 이름 문자열.
 *
 * @param deliveredAt 배송 완료 전이면 null
 */
public record DeliveryResult(
    Long deliveryId,
    Long orderId,
    Long sellerId,
    String status,
    String carrier,
    String trackingNumber,
    LocalDateTime shippedAt,
    LocalDateTime deliveredAt) {

    public static DeliveryResult from(Delivery delivery) {
        return new DeliveryResult(
            delivery.getId(),
            delivery.getOrderId(),
            delivery.getSellerId(),
            delivery.getStatus().name(),
            delivery.getCarrier() == null ? null : delivery.getCarrier().name(),
            delivery.getTrackingNumber(),
            delivery.getShippedAt(),
            delivery.getDeliveredAt());
    }
}
