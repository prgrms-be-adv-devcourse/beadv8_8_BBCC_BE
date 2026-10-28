package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 상세. 목록 필드에 주문상품별 금액을 더한다.
 *
 * @param status 결제 상태 이름(PaymentStatus)
 * @param failureCode 실패 결제만 값이 있다(PaymentFailureCode 이름)
 * @param paidAt 실패 결제는 null
 * @param items 주문상품 순서(ID 오름차순). 실패 결제는 빈 목록
 */
public record PaymentDetail(Long paymentId, Long orderId, long amount, long refundedAmount, String status,
                            String failureCode, LocalDateTime paidAt, LocalDateTime createdAt, List<Item> items) {

    public record Item(Long orderItemId, long amount, long refundedAmount) {
    }

    public static PaymentDetail from(Payment payment) {
        PaymentSummary summary = PaymentSummary.from(payment);
        return new PaymentDetail(summary.paymentId(), summary.orderId(), summary.amount(),
            summary.refundedAmount(), summary.status(), summary.failureCode(), summary.paidAt(),
            summary.createdAt(),
            payment.getItems().stream()
                .map(item -> new Item(item.getOrderItemId(), item.getAmount(), item.getRefundedAmount()))
                .toList());
    }
}
