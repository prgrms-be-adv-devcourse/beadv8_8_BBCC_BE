package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import java.time.LocalDateTime;

/**
 * 결제 목록의 한 줄.
 *
 * @param memberId 결제한 회원 ID. 관리자 조회에서 쓴다
 * @param status 결제 상태 이름(PaymentStatus)
 * @param failureCode 실패 결제만 값이 있다(PaymentFailureCode 이름)
 * @param paidAt 실패 결제는 null
 */
public record PaymentSummary(Long paymentId, Long memberId, Long orderId, long amount, long refundedAmount,
                             String status, String failureCode, LocalDateTime paidAt, LocalDateTime createdAt) {

    public static PaymentSummary from(Payment payment) {
        return new PaymentSummary(payment.getId(), payment.getMemberId(), payment.getOrderId(), payment.getAmount(),
            payment.getRefundedAmount(), payment.getStatus().name(),
            payment.getFailureCode() == null ? null : payment.getFailureCode().name(),
            payment.getPaidAt(), payment.getCreatedAt());
    }
}
