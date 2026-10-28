package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentDetail;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @param failureCode 실패 결제만 값이 있고 나머지는 null
 * @param paidAt 실패 결제는 null
 * @param items 실패 결제는 빈 목록
 */
public record PaymentDetailResponse(Long paymentId, Long orderId, long amount, long refundedAmount, String status,
                                    String failureCode, LocalDateTime paidAt, LocalDateTime createdAt,
                                    List<Item> items) {

    public record Item(Long orderItemId, long amount, long refundedAmount) {
    }

    public static PaymentDetailResponse from(PaymentDetail detail) {
        return new PaymentDetailResponse(detail.paymentId(), detail.orderId(), detail.amount(),
            detail.refundedAmount(), detail.status(), detail.failureCode(), detail.paidAt(), detail.createdAt(),
            detail.items().stream()
                .map(item -> new Item(item.orderItemId(), item.amount(), item.refundedAmount()))
                .toList());
    }
}
