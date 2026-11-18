package com.bbcc.kidly.boundedcontext.notification.app.dto;

/**
 * @param confirmType 확정 방식(DIRECT · AUTO). 이 칸이 생기기 전 메시지는 null
 */
public record PurchaseConfirmedCommand(Long orderItemId, Long orderId, Long memberId, Long sellerId, Long productId,
    int quantity, String confirmType) {
}
