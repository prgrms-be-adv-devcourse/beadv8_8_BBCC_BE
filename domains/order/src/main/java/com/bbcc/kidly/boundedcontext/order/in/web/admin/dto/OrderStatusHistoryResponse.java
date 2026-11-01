package com.bbcc.kidly.boundedcontext.order.in.web.admin.dto;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderStatusHistoryResult;
import java.time.LocalDateTime;

/**
 * 관리자 주문 상태 이력 한 줄(#442). ID는 문자열(기획서 6.1), 없는 값은 null.
 */
public record OrderStatusHistoryResponse(
    String historyId,
    String targetType,
    String targetId,
    String fromStatus,
    String toStatus,
    String reason,
    String actorType,
    String actorId,
    String source,
    LocalDateTime occurredAt) {

    public static OrderStatusHistoryResponse from(OrderStatusHistoryResult result) {
        return new OrderStatusHistoryResponse(String.valueOf(result.historyId()), result.targetType(),
            String.valueOf(result.targetId()), result.fromStatus(), result.toStatus(), result.reason(),
            result.actorType(), result.actorId() == null ? null : String.valueOf(result.actorId()), result.source(),
            result.occurredAt());
    }
}
