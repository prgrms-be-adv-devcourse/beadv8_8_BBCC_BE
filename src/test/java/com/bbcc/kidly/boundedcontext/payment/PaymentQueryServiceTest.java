package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentQueryService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentDetail;
import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    private static final Long PAYMENT_ID = 7L;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentQueryService paymentQueryService;

    @Test
    @DisplayName("내 결제의 상세는 주문상품별 금액과 함께 준다")
    void getPayment() {
        Payment payment = PaymentFixture.completedPayment();
        payment.refund(1000L, 4_000L);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

        PaymentDetail detail = paymentQueryService.getPayment(MEMBER_ID, PAYMENT_ID);

        assertThat(detail.orderId()).isEqualTo(ORDER_ID);
        assertThat(detail.amount()).isEqualTo(15_000L);
        assertThat(detail.refundedAmount()).isEqualTo(4_000L);
        assertThat(detail.status()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(detail.failureCode()).isNull();
        assertThat(detail.items()).containsExactly(
            new PaymentDetail.Item(1000L, 10_000L, 4_000L),
            new PaymentDetail.Item(1001L, 5_000L, 0L));
    }

    @Test
    @DisplayName("실패 결제는 실패 사유가 있고 결제 시각 · 주문상품은 없다")
    void failedPayment() {
        Payment payment = Payment.fail(ORDER_ID, PaymentFixture.WALLET_ID, MEMBER_ID, 15_000L,
            PaymentFailureCode.INSUFFICIENT_BALANCE);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

        PaymentDetail detail = paymentQueryService.getPayment(MEMBER_ID, PAYMENT_ID);

        assertThat(detail.status()).isEqualTo("FAILED");
        assertThat(detail.failureCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(detail.paidAt()).isNull();
        assertThat(detail.items()).isEmpty();
    }

    @Test
    @DisplayName("남의 결제는 없는 결제처럼 PAYMENT_NOT_FOUND다")
    void othersPayment() {
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(PaymentFixture.completedPayment()));

        assertThatThrownBy(() -> paymentQueryService.getPayment(2L, PAYMENT_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 결제는 PAYMENT_NOT_FOUND다")
    void notFound() {
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentQueryService.getPayment(MEMBER_ID, PAYMENT_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }
}
