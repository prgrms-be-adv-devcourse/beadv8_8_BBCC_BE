package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.Refund;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundFailureCode;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundItem;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundStatus;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefundTest {

    private static final Long PAYMENT_ID = 50L;
    private static final Long CANCELLATION_ID = 70L;

    @Test
    @DisplayName("환불이 완료되면 금액은 환불 항목 금액의 합이다")
    void completeSumsItemAmounts() {
        Refund refund = Refund.complete(PAYMENT_ID, PaymentFixture.ORDER_ID, CANCELLATION_ID,
            List.of(RefundItem.of(500L, 3_000L), RefundItem.of(501L, 2_000L)), NOW);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getAmount()).isEqualTo(5_000L);
        assertThat(refund.getRefundedAt()).isEqualTo(NOW);
        assertThat(refund.getItems()).hasSize(2)
            .allSatisfy(item -> assertThat(item.getRefund()).isSameAs(refund));
    }

    @Test
    @DisplayName("환불 항목 없이는 환불을 완료할 수 없다")
    void completeRequiresItems() {
        assertThatThrownBy(() -> Refund.complete(
            PAYMENT_ID, PaymentFixture.ORDER_ID, CANCELLATION_ID, List.of(), NOW))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("0 이하 금액은 환불 항목이 될 수 없다")
    void itemRequiresPositiveAmount() {
        assertThatThrownBy(() -> RefundItem.of(500L, 0L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("환불이 실패하면 실패 사유가 남고 환불 시각은 없다")
    void failRecordsFailedRefund() {
        Refund refund = Refund.fail(PAYMENT_ID, PaymentFixture.ORDER_ID, CANCELLATION_ID, 5_000L,
            RefundFailureCode.REFUND_AMOUNT_EXCEEDED);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureCode()).isEqualTo(RefundFailureCode.REFUND_AMOUNT_EXCEEDED);
        assertThat(refund.getAmount()).isEqualTo(5_000L);
        assertThat(refund.getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("주문 취소로 환불하면 주문상품 취소 없이(취소 ID null) 환불 완료로 남는다")
    void completeForOrderCancellation() {
        Refund refund = Refund.completeForOrderCancellation(PAYMENT_ID, PaymentFixture.ORDER_ID,
            List.of(RefundItem.of(500L, 10_000L), RefundItem.of(501L, 5_000L)), NOW);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getCancellationId()).isNull();
        assertThat(refund.getAmount()).isEqualTo(15_000L);
        assertThat(refund.getRefundedAt()).isEqualTo(NOW);
        assertThat(refund.getItems()).hasSize(2);
    }
}
