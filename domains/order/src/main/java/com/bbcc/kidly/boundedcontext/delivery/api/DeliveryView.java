package com.bbcc.kidly.boundedcontext.delivery.api;

import java.time.LocalDateTime;

/**
 * 다른 컨텍스트가 읽는 판매자별 배송 한 건(#617). 상태 · 택배사는 enum 이름 문자열.
 *
 * @param shipped 발송됐는지(배송 중 · 배송 완료)
 * @param deliveredAt 배송 완료 전이면 null
 */
public record DeliveryView(
    Long deliveryId,
    Long orderId,
    Long sellerId,
    String status,
    String carrier,
    String trackingNumber,
    LocalDateTime shippedAt,
    LocalDateTime deliveredAt,
    boolean shipped) {

    /** 발송 전(배송 준비) 상태 이름 */
    public static final String READY = "READY";
}
