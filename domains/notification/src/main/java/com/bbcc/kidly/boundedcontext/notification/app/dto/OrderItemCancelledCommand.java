package com.bbcc.kidly.boundedcontext.notification.app.dto;

/**
 * 구매자 알림함에 넣을 판매자 취소(주문상품 일부).
 *
 * @param cancellationId 이 취소의 ID. 같은 취소는 한 건만 넣는다
 * @param reason 판매자 취소 사유 이름(OUT_OF_STOCK · WRONG_PRODUCT_INFO · OTHER)
 * @param itemCount 이번에 취소한 주문상품 수
 */
public record OrderItemCancelledCommand(Long cancellationId, Long orderId, Long memberId, String reason,
                                        int itemCount) {
}
