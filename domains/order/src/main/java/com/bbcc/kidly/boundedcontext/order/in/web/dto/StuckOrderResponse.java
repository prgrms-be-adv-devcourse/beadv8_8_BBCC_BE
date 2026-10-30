package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import com.bbcc.kidly.boundedcontext.order.app.stuck.dto.StuckOrderResult;
import java.time.LocalDateTime;

/**
 * 관리자 멈춘 주문 목록 한 줄(#445). ID는 문자열(기획서 6.1).
 *
 * @param orderId 관련 주문 ID. 아웃박스 멈춤에서 메시지 키가 주문 ID가 아니면 null
 * @param since 멈춘 것으로 보기 시작한 시각(저장된 시각 + 기준 시간)
 * @param outbox 아웃박스 멈춤(OUTBOX_STUCK, #602)일 때만 있는 메시지 정보. 다른 종류는 null
 */
public record StuckOrderResponse(
    String kind,
    String orderId,
    String targetType,
    String targetId,
    String status,
    LocalDateTime since,
    Outbox outbox) {

    public static StuckOrderResponse from(StuckOrderResult result) {
        return new StuckOrderResponse(result.kind(), idOf(result.orderId()), result.targetType(),
            idOf(result.targetId()), result.status(), result.since(), Outbox.from(result.outbox()));
    }

    /**
     * 보내지 못한 맨 앞 주문 이벤트 아웃박스 메시지.
     *
     * @param lastError 마지막 전송 실패 이유. 시도 전이면 null
     * @param backlogCount 이 메시지 뒤에 밀린 미전송 메시지 수
     */
    public record Outbox(String outboxId, String eventType, String eventId, int attemptCount,
        LocalDateTime createdAt, String lastError, long backlogCount) {

        static Outbox from(StuckOrderResult.Outbox outbox) {
            if (outbox == null) {
                return null;
            }
            return new Outbox(String.valueOf(outbox.outboxId()), outbox.eventType(), outbox.eventId().toString(),
                outbox.attemptCount(), outbox.createdAt(), outbox.lastError(), outbox.backlogCount());
        }
    }

    private static String idOf(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
