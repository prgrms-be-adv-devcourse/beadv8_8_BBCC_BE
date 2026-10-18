package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import java.time.LocalDateTime;

/**
 * 주문 상태 이력 한 줄(#442). 대상 종류 · 주체 종류는 이름으로 넘긴다(in은 도메인 enum을 쓰지 않음).
 *
 * @param fromStatus 새로 만들었으면 null
 * @param reason 취소 사유 · 확정 방식 · 결제 반영 실패 코드. 없으면 null
 * @param actorId 회원 ID. 시스템 · 이벤트는 null
 */
public record OrderStatusHistoryResult(
    Long historyId,
    String targetType,
    Long targetId,
    String fromStatus,
    String toStatus,
    String reason,
    String actorType,
    Long actorId,
    String source,
    LocalDateTime occurredAt) {

    public static OrderStatusHistoryResult from(OrderStatusHistory history) {
        return new OrderStatusHistoryResult(history.getId(), history.getTargetType().name(), history.getTargetId(),
            history.getFromStatus(), history.getToStatus(), history.getReason(), history.getActorType().name(),
            history.getActorId(), history.getSource(), history.getOccurredAt());
    }
}
