package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import java.time.LocalDateTime;

/**
 * 주문 목록의 한 줄. 상태는 enum 이름 문자열.
 *
 * @param orderedAt 주문 시각(생성 시각)
 */
public record OrderSummary(Long orderId, String status, long totalAmount, LocalDateTime orderedAt) {

    public static OrderSummary from(Order order) {
        return new OrderSummary(order.getId(), order.getStatus().name(), order.getTotalAmount(), order.getCreatedAt());
    }
}
