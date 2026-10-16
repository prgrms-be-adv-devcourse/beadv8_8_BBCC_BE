package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 완료 반영 실패 기록(#353). 상태는 enum 이름 문자열.
 *
 * @param paymentId 받은 결제 완료의 결제 ID(결제 쪽이 빠뜨렸으면 null)
 * @param failureCode 마지막 실패 이유(공백 없는 코드)
 * @param attemptCount 실패한 횟수(처음 실패 1)
 */
public record PaymentRecoveryResult(
    Long recoveryId,
    Long orderId,
    Long paymentId,
    Long memberId,
    long amount,
    List<Item> items,
    String status,
    String failureCode,
    int attemptCount,
    LocalDateTime lastAttemptedAt,
    LocalDateTime createdAt) {

    public record Item(Long orderItemId, long amount) {
    }

    public static PaymentRecoveryResult from(PaymentRecovery recovery) {
        return new PaymentRecoveryResult(
            recovery.getId(),
            recovery.getOrderId(),
            recovery.getPaymentId(),
            recovery.getMemberId(),
            recovery.getAmount(),
            recovery.getItems().stream().map(item -> new Item(item.orderItemId(), item.amount())).toList(),
            recovery.getStatus().name(),
            recovery.getFailureCode(),
            recovery.getAttemptCount(),
            recovery.getLastAttemptedAt(),
            recovery.getCreatedAt());
    }
}
