package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import com.bbcc.kidly.boundedcontext.order.app.stuck.dto.StuckOrderResult;
import java.time.LocalDateTime;

/**
 * 관리자 멈춘 주문 목록 한 줄(#445). ID는 문자열(기획서 6.1).
 *
 * @param since 멈춘 것으로 보기 시작한 시각(저장된 시각 + 기준 시간)
 */
public record StuckOrderResponse(
    String kind,
    String orderId,
    String targetType,
    String targetId,
    String status,
    LocalDateTime since) {

    public static StuckOrderResponse from(StuckOrderResult result) {
        return new StuckOrderResponse(result.kind(), String.valueOf(result.orderId()), result.targetType(),
            String.valueOf(result.targetId()), result.status(), result.since());
    }
}
