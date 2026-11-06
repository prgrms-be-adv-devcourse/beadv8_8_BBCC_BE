package com.bbcc.kidly.boundedcontext.notification.app.dto;

/**
 * 구매확정된 주문상품 하나. 판매자 알림함에 넣고, 자동 확정이면 구매자 알림함에도 넣는다.
 *
 * @param memberId 구매자(회원 ID). 자동 확정이면 이 회원의 알림함에 넣는다
 * @param sellerId 판매자(회원 ID). 이 판매자의 알림함에 넣는다
 * @param confirmType 확정 방식(DIRECT · AUTO). 이 칸이 생기기 전 메시지는 null
 */
public record PurchaseConfirmedCommand(Long orderItemId, Long orderId, Long memberId, Long sellerId, Long productId,
    int quantity, String confirmType) {
}
