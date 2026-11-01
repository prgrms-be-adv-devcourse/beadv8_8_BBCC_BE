package com.bbcc.kidly.boundedcontext.order.in.web.admin.dto;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderEventFailureResult;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 이벤트 처리 실패 기록(관리자, #449). ID는 문자열.
 *
 * @param eventType STOCK_DEDUCTED(재고 차감됨) · STOCK_DEDUCTION_FAILED(재고 차감 실패) · PAYMENT_FAILED(결제 실패)
 * @param stockFailures 재고 차감 실패가 보낸 주문상품별 사유. 다른 종류는 빈 목록
 * @param status PENDING(재처리 대기) · RECOVERED(복구됨) · CANCELLED(종료됨)
 * @param failureCode 마지막 실패 이유(공백 없는 코드)
 * @param attemptCount 실패한 횟수(처음 실패 1)
 */
public record OrderEventFailureResponse(
    String failureId,
    String eventType,
    String orderId,
    List<StockFailure> stockFailures,
    String status,
    String failureCode,
    int attemptCount,
    LocalDateTime lastAttemptedAt,
    LocalDateTime createdAt) {

    /**
     * @param reason 상품 도메인이 보낸 사유 이름(예: OUT_OF_STOCK)
     */
    public record StockFailure(String orderItemId, String reason) {
    }

    public static OrderEventFailureResponse from(OrderEventFailureResult result) {
        return new OrderEventFailureResponse(
            String.valueOf(result.failureId()),
            result.eventType(),
            String.valueOf(result.orderId()),
            result.stockFailures().stream()
                .map(item -> new StockFailure(String.valueOf(item.orderItemId()), item.reason()))
                .toList(),
            result.status(),
            result.failureCode(),
            result.attemptCount(),
            result.lastAttemptedAt(),
            result.createdAt());
    }
}
