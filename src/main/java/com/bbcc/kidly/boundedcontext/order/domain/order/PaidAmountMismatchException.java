package com.bbcc.kidly.boundedcontext.order.domain.order;

/**
 * 결제대기 주문에 주문 총액과 다른 금액의 결제 완료가 왔다. 결제 쪽 버그로만 생기는 불변식 위반이라 API 오류가 아니다.
 */
public class PaidAmountMismatchException extends RuntimeException {

    public PaidAmountMismatchException(Long orderId, long totalAmount, long paidAmount) {
        super("결제 금액이 주문 총액과 다릅니다: orderId=" + orderId + " totalAmount=" + totalAmount
            + " paidAmount=" + paidAmount);
    }
}
