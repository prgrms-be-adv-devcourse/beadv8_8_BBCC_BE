package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 받은 이벤트 처리 실패 기록(#449). 이벤트 종류 · 상태는 enum 이름 문자열.
 *
 * @param stockFailures 재고 차감 실패의 주문상품별 사유. 다른 종류는 빈 목록
 * @param failureCode 마지막 실패 이유(공백 없는 코드)
 * @param attemptCount 실패한 횟수(처음 실패 1)
 */
public record OrderEventFailureResult(
    Long failureId,
    String eventType,
    Long orderId,
    List<StockFailure> stockFailures,
    String status,
    String failureCode,
    int attemptCount,
    LocalDateTime lastAttemptedAt,
    LocalDateTime createdAt) {

    public record StockFailure(Long orderItemId, String reason) {
    }

    public static OrderEventFailureResult from(OrderEventFailure failure) {
        return new OrderEventFailureResult(
            failure.getId(),
            failure.getEventType().name(),
            failure.getOrderId(),
            failure.getStockFailures().stream()
                .map(item -> new StockFailure(item.orderItemId(), item.reason()))
                .toList(),
            failure.getStatus().name(),
            failure.getFailureCode(),
            failure.getAttemptCount(),
            failure.getLastAttemptedAt(),
            failure.getCreatedAt());
    }
}
