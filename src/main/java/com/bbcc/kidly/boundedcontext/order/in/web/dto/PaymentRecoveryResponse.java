package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.PaymentRecoveryResult;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 반영 실패 기록(관리자, #353). ID는 문자열 · 금액은 원 단위 정수.
 *
 * @param paymentId 결제 쪽이 결제 ID를 빠뜨린 결제 완료면 null
 * @param memberId 결제 쪽이 회원을 빠뜨린 결제 완료면 null
 * @param status PENDING(재처리 대기) · RECOVERED(복구됨) · CANCELLED(취소로 종료) · NOT_RETRYABLE(재처리 불가)
 * @param failureCode 마지막 실패 이유(공백 없는 코드)
 * @param attemptCount 실패한 횟수(처음 실패 1)
 */
public record PaymentRecoveryResponse(
    String recoveryId,
    String orderId,
    String paymentId,
    String memberId,
    long amount,
    List<Item> items,
    String status,
    String failureCode,
    int attemptCount,
    LocalDateTime lastAttemptedAt,
    LocalDateTime createdAt) {

    /**
     * @param orderItemId 결제 쪽이 빈 칸으로 보낸 주문상품이면 null
     */
    public record Item(String orderItemId, long amount) {
    }

    public static PaymentRecoveryResponse from(PaymentRecoveryResult result) {
        return new PaymentRecoveryResponse(
            toText(result.recoveryId()),
            toText(result.orderId()),
            toText(result.paymentId()),
            toText(result.memberId()),
            result.amount(),
            result.items().stream().map(item -> new Item(toText(item.orderItemId()), item.amount())).toList(),
            result.status(),
            result.failureCode(),
            result.attemptCount(),
            result.lastAttemptedAt(),
            result.createdAt());
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
