package com.bbcc.kidly.boundedcontext.order.app.stuck.dto;

import java.time.LocalDateTime;

/**
 * 멈춘 주문 한 줄(#445).
 *
 * @param since 멈춘 것으로 보기 시작한 시각(저장된 시각 + 기준 시간)
 */
public record StuckOrderResult(
    String kind,
    Long orderId,
    String targetType,
    Long targetId,
    String status,
    LocalDateTime since) {
}
