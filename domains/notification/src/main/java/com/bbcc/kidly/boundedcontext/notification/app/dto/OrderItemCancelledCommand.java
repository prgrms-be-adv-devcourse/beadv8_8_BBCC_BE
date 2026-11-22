package com.bbcc.kidly.boundedcontext.notification.app.dto;

/**
 * @param cancellationId 이 취소의 ID. 같은 취소는 한 건만 넣는다
 * @param reason 판매자 취소 사유 이름(OUT_OF_STOCK, WRONG_PRODUCT_INFO, OTHER)
 */
public record OrderItemCancelledCommand(Long cancellationId, Long orderId, Long memberId, String reason,
                                        int itemCount) {
}
