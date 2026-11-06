package com.bbcc.kidly.boundedcontext.notification.app.dto;

/**
 * 구매확정된 주문상품 하나. 판매자 알림함에 넣는다.
 *
 * @param sellerId 판매자(회원 ID). 이 판매자의 알림함에 넣는다
 */
public record PurchaseConfirmedCommand(Long orderItemId, Long sellerId, Long productId, int quantity) {
}
