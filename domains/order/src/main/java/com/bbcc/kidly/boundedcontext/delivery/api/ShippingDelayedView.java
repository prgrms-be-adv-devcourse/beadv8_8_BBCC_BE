package com.bbcc.kidly.boundedcontext.delivery.api;

import java.time.LocalDateTime;

/**
 * 발송 전(READY)으로 남은 배송 한 건(#617-2). 관리자 멈춘 주문(발송 지연)이 읽는다.
 *
 * @param createdAt 배송 생성 시각(멈춤 기준 시각)
 */
public record ShippingDelayedView(Long orderId, Long deliveryId, LocalDateTime createdAt) {
}
