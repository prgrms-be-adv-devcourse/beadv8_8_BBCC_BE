package com.bbcc.kidly.boundedcontext.notification.app.dto;

import java.time.LocalDateTime;

/**
 * @param deliveryId 배송 ID. 같은 배송의 출발, 완료는 한 건씩만 넣는다
 * @param autoConfirmAt 자동 구매확정 예정 시각. 배송 완료에만 있고, 출발, 예전 메시지는 null
 */
public record DeliveryCommand(Long deliveryId, Long orderId, Long memberId, String carrierName,
    String trackingNumber, LocalDateTime autoConfirmAt) {
}
