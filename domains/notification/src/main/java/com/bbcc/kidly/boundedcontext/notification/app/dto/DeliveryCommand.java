package com.bbcc.kidly.boundedcontext.notification.app.dto;

/**
 * 배송 출발 · 완료를 알림함에 넣을 배송 한 건.
 *
 * @param deliveryId 배송 ID. 같은 배송의 출발 · 완료는 한 건씩만 넣는다
 * @param memberId 주문한 회원(구매자) ID. 이 회원의 알림함에 넣는다
 */
public record DeliveryCommand(Long deliveryId, Long orderId, Long memberId, String carrierName,
    String trackingNumber) {
}
