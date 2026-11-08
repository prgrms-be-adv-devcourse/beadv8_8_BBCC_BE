package com.bbcc.kidly.boundedcontext.order.app.stuck.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 멈춘 주문 한 줄(#445).
 *
 * @param orderId 관련 주문 ID. 아웃박스 멈춤은 메시지 키(주문 ID)이고, 숫자가 아니면 null
 * @param since 멈춘 것으로 보기 시작한 시각(저장된 시각 + 기준 시간)
 * @param outbox 아웃박스 멈춤(#602)일 때만 있는 메시지 정보. 다른 종류는 null
 * @param aiMemo 이 주문 · 종류의 가장 최근 AI 진단 메모(#926). 없으면 null
 */
public record StuckOrderResult(
    String kind,
    Long orderId,
    String targetType,
    Long targetId,
    String status,
    LocalDateTime since,
    Outbox outbox,
    AiMemo aiMemo) {

    public StuckOrderResult(String kind, Long orderId, String targetType, Long targetId, String status,
        LocalDateTime since) {
        this(kind, orderId, targetType, targetId, status, since, null, null);
    }

    public StuckOrderResult(String kind, Long orderId, String targetType, Long targetId, String status,
        LocalDateTime since, Outbox outbox) {
        this(kind, orderId, targetType, targetId, status, since, outbox, null);
    }

    public StuckOrderResult withAiMemo(AiMemo memo) {
        return new StuckOrderResult(kind, orderId, targetType, targetId, status, since, outbox, memo);
    }

    /**
     * AI 진단 메모(#926). AI의 제안일 뿐이고 조치는 관리자가 한다.
     *
     * @param confidence LOW · MEDIUM · HIGH
     * @param provider 답한 곳(예: api.groq.com · fake)
     * @param createdAt 메모를 남긴 시각
     */
    public record AiMemo(String summary, String recommendedAction, String confidence, String provider, String model,
        LocalDateTime createdAt) {
    }

    /**
     * 보내지 못한 맨 앞 아웃박스 메시지(#602).
     *
     * @param createdAt 메시지를 처음 만든 시각
     * @param lastError 마지막 전송 실패 이유(예외 종류 · 메시지, 본문 없음). 시도 전이면 null
     * @param backlogCount 이 메시지 뒤에 밀린 미전송 메시지 수
     */
    public record Outbox(Long outboxId, String eventType, UUID eventId, int attemptCount, LocalDateTime createdAt,
        String lastError, long backlogCount, LocalDateTime quarantinedAt) {

        public Outbox(Long outboxId, String eventType, UUID eventId, int attemptCount, LocalDateTime createdAt,
            String lastError, long backlogCount) {
            this(outboxId, eventType, eventId, attemptCount, createdAt, lastError, backlogCount, null);
        }
    }
}
