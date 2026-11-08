package com.bbcc.kidly.boundedcontext.notification.app.dto;

import java.time.LocalDateTime;

/**
 * 곧 자동으로 구매확정될 주문상품 하나. 구매자 알림함에 넣고 웹 푸시로 보낸다.
 *
 * @param memberId 구매자(회원 ID). 이 회원의 알림함에 넣는다
 * @param confirmAt 자동 구매확정 예정 시각(주문이 정한 값)
 */
public record AutoConfirmScheduledCommand(Long orderItemId, Long orderId, Long memberId, Long productId, int quantity,
    LocalDateTime confirmAt) {
}
