package com.bbcc.kidly.boundedcontext.order.in.mcp;

import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.codeOrNull;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.exceptionClassOnly;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.failureCodeOrNull;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.idOrNull;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.receivedEventTypeOrNull;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.sourceOrNull;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.OrderDeadLetterSummary;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OrderDeadLetterSummaryResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OrderEventFailureResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OrderStatusHistoryResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OutboxQuarantineResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.PaymentRecoveryResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.StuckOrderResponse;

/**
 * MCP 응답 전용 변환(#925). 칸은 관리자 API와 같게 두되, 자유 문구가 될 수 있는 칸은 {@link
 * com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard}로 걸러 정해진 형식(코드 · 예외 클래스 이름 · 경로 틀 · 이벤트 타입 · ID)만
 * 내보낸다(아니면 null). 관리자 API 응답은 바꾸지 않는다.
 */
final class OrderMcpViews {

    private OrderMcpViews() {
    }

    static StuckOrderResponse stuck(StuckOrderResponse row) {
        StuckOrderResponse.Outbox outbox = row.outbox();
        return new StuckOrderResponse(row.kind(), row.orderId(), row.targetType(), row.targetId(), row.status(),
            row.since(), outbox == null ? null : new StuckOrderResponse.Outbox(outbox.outboxId(), outbox.eventType(),
                outbox.eventId(), outbox.attemptCount(), outbox.createdAt(), exceptionClassOnly(outbox.lastError()),
                outbox.backlogCount(), outbox.quarantinedAt()));
    }

    static OrderStatusHistoryResponse history(OrderStatusHistoryResponse row) {
        return new OrderStatusHistoryResponse(row.historyId(), row.targetType(), row.targetId(), row.fromStatus(),
            row.toStatus(), failureCodeOrNull(row.reason()), row.actorType(), row.actorId(), sourceOrNull(row.source()),
            row.occurredAt());
    }

    // 받은 eventType은 앞 마디가 원래 토픽(kidly.{x}.events)의 x일 때만
    static OrderDeadLetterSummaryResponse deadLetter(OrderDeadLetterSummary summary) {
        OrderDeadLetterSummaryResponse row = OrderDeadLetterSummaryResponse.from(summary);
        return new OrderDeadLetterSummaryResponse(row.deadLetterId(), row.lastReceivedAt(), row.failureType(),
            receivedEventTypeOrNull(row.eventType(), summary.sourceTopic()), row.orderId(), row.receivedCount(),
            row.status(), row.paymentEvent());
    }

    static OrderEventFailureResponse eventFailure(OrderEventFailureResponse row) {
        return new OrderEventFailureResponse(row.failureId(), row.eventType(), row.orderId(),
            row.stockFailures().stream()
                .map(item -> new OrderEventFailureResponse.StockFailure(item.orderItemId(), codeOrNull(item.reason())))
                .toList(),
            row.status(), failureCodeOrNull(row.failureCode()), row.attemptCount(), row.lastAttemptedAt(),
            row.createdAt());
    }

    static PaymentRecoveryResponse paymentRecovery(PaymentRecoveryResponse row) {
        return new PaymentRecoveryResponse(row.recoveryId(), row.orderId(), row.paymentId(), row.memberId(),
            row.amount(), row.items(), row.status(), failureCodeOrNull(row.failureCode()), row.attemptCount(),
            row.lastAttemptedAt(), row.createdAt());
    }

    static OutboxQuarantineResponse outbox(OutboxQuarantineResponse row) {
        return new OutboxQuarantineResponse(row.outboxId(), row.eventId(), row.eventType(), row.topic(),
            idOrNull(row.messageKey()), row.status(), row.attemptCount(), exceptionClassOnly(row.lastError()),
            row.createdAt(), row.quarantinedAt(), row.sentAt(), row.lastRetriedAt(), row.lastRetriedBy(),
            row.retryCount());
    }
}
