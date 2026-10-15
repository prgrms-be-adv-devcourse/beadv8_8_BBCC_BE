package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentSummary;
import java.time.LocalDateTime;

/**
 * 관리자 결제 목록의 한 줄. 내 결제 내역 한 줄에 결제한 회원을 더한 모양이다.
 *
 * @param failureCode 실패 결제만 값이 있고 나머지는 null
 * @param paidAt 실패 결제는 null
 */
public record AdminPaymentResponse(Long paymentId, Long memberId, Long orderId, long amount, long refundedAmount,
                                   String status, String failureCode, LocalDateTime paidAt,
                                   LocalDateTime createdAt) {

    public static AdminPaymentResponse from(PaymentSummary summary) {
        return new AdminPaymentResponse(summary.paymentId(), summary.memberId(), summary.orderId(),
            summary.amount(), summary.refundedAmount(), summary.status(), summary.failureCode(), summary.paidAt(),
            summary.createdAt());
    }
}
