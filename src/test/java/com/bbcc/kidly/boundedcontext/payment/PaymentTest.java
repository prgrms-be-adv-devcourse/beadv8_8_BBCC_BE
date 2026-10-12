package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentItem;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentStatus;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    @DisplayName("결제가 완료되면 금액은 주문상품 금액의 합이다")
    void completeSumsItemAmounts() {
        Payment payment = PaymentFixture.completedPayment();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getAmount()).isEqualTo(15_000L);
        assertThat(payment.getRefundedAmount()).isZero();
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
        assertThat(payment.getItems()).hasSize(2)
            .allSatisfy(item -> assertThat(item.getPayment()).isSameAs(payment));
    }

    @Test
    @DisplayName("주문상품 없이는 결제를 완료할 수 없다")
    void completeRequiresItems() {
        assertThatThrownBy(() -> Payment.complete(
            PaymentFixture.ORDER_ID, PaymentFixture.WALLET_ID, PaymentFixture.MEMBER_ID, List.of(), NOW))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("0 이하 금액의 주문상품은 결제할 수 없다")
    void itemRequiresPositiveAmount() {
        assertThatThrownBy(() -> PaymentItem.of(1000L, 0L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("결제가 실패하면 실패 사유가 남고 결제 시각은 없다")
    void failRecordsFailureCode() {
        Payment payment = Payment.fail(PaymentFixture.ORDER_ID, PaymentFixture.WALLET_ID, PaymentFixture.MEMBER_ID,
            15_000L, PaymentFailureCode.INSUFFICIENT_BALANCE);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo(PaymentFailureCode.INSUFFICIENT_BALANCE);
        assertThat(payment.getPaidAt()).isNull();
        assertThat(payment.getItems()).isEmpty();
    }

    @Test
    @DisplayName("주문상품 일부를 환불하면 부분 환불 상태가 된다")
    void partialRefund() {
        Payment payment = PaymentFixture.completedPayment();

        PaymentItem refunded = payment.refund(1000L, 10_000L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(10_000L);
        assertThat(refunded.getOrderItemId()).isEqualTo(1000L);
        assertThat(refunded.getRefundedAmount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("결제 금액을 모두 환불하면 환불 완료 상태가 된다")
    void fullRefund() {
        Payment payment = PaymentFixture.completedPayment();

        payment.refund(1000L, 10_000L);
        payment.refund(1001L, 5_000L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("주문상품 결제 금액보다 많이 환불할 수 없다")
    void refundCannotExceedItemAmount() {
        Payment payment = PaymentFixture.completedPayment();
        payment.refund(1001L, 3_000L);

        assertThatThrownBy(() -> payment.refund(1001L, 2_001L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.REFUND_AMOUNT_EXCEEDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("결제에 없는 주문상품은 환불할 수 없다")
    void refundUnknownItemFails() {
        Payment payment = PaymentFixture.completedPayment();

        assertThatThrownBy(() -> payment.refund(9999L, 1_000L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("실패한 결제는 환불할 수 없다")
    void refundFailedPaymentFails() {
        Payment payment = Payment.fail(PaymentFixture.ORDER_ID, PaymentFixture.WALLET_ID, PaymentFixture.MEMBER_ID,
            15_000L, PaymentFailureCode.INSUFFICIENT_BALANCE);

        assertThatThrownBy(() -> payment.refund(1000L, 1_000L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
    }
}
