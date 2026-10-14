package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentSummary;
import java.time.LocalDateTime;

/**
 * @param failureCode 실패 결제만 값이 있고 나머지는 null
 * @param paidAt 실패 결제는 null
 */
public record PaymentSummaryResponse(Long paymentId, Long orderId, long amount, long refundedAmount, String status,
                                     String failureCode, LocalDateTime paidAt, LocalDateTime createdAt) {

    public static PaymentSummaryResponse from(PaymentSummary summary) {
        return new PaymentSummaryResponse(summary.paymentId(), summary.orderId(), summary.amount(),
            summary.refundedAmount(), summary.status(), summary.failureCode(), summary.paidAt(),
            summary.createdAt());
    }
}
