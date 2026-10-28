package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.CANCELLATION_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.CHARGE_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.ChargeCancelService;
import com.bbcc.kidly.boundedcontext.payment.app.ChargeService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeCancelResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeCancelTarget;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentKeyCipher;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgCancellation;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChargeCancelServiceTest {

    private static final ChargeCancelTarget TARGET =
        new ChargeCancelTarget(CHARGE_ID, CANCELLATION_ID, "encrypted", "예치금 충전 취소", 10_000L);
    private static final String IDEMPOTENCY_KEY = "charge-cancellation-" + CANCELLATION_ID;

    @Mock
    private ChargeService chargeService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentKeyCipher paymentKeyCipher;

    @InjectMocks
    private ChargeCancelService chargeCancelService;

    @BeforeEach
    void decrypt() {
        given(paymentKeyCipher.decrypt("encrypted")).willReturn("payment-key");
    }

    @Test
    @DisplayName("토스가 취소하면 취소 완료 결과를 돌려준다. 멱등 키는 취소 요청 ID로 만든다")
    void cancelled() {
        given(chargeService.startCancel(MEMBER_ID, CHARGE_ID)).willReturn(TARGET);
        PgCancellation cancellation = new PgCancellation(LocalDateTime.now());
        given(paymentGateway.cancel("payment-key", "예치금 충전 취소", IDEMPOTENCY_KEY))
            .willReturn(new PgResult.Success<>(cancellation));
        ChargeCancelResult done = new ChargeCancelResult(CHARGE_ID, "CANCELLED", 10_000L, 0L);
        given(chargeService.completeCancel(CHARGE_ID, cancellation)).willReturn(done);

        assertThat(chargeCancelService.cancel(MEMBER_ID, CHARGE_ID)).isEqualTo(done);
    }

    @Test
    @DisplayName("토스가 거절하면 취소 실패를 반영하고 토스 메시지로 CHARGE_CANCEL_REJECTED를 던진다")
    void rejected() {
        given(chargeService.startCancel(MEMBER_ID, CHARGE_ID)).willReturn(TARGET);
        given(paymentGateway.cancel("payment-key", "예치금 충전 취소", IDEMPOTENCY_KEY))
            .willReturn(new PgResult.Rejected<>("NOT_CANCELABLE_PAYMENT", "취소할 수 없는 결제입니다"));

        assertThatThrownBy(() -> chargeCancelService.cancel(MEMBER_ID, CHARGE_ID))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.CHARGE_CANCEL_REJECTED);
                assertThat(e.getDetail()).isEqualTo("취소할 수 없는 결제입니다");
            });
        then(chargeService).should().failCancel(CHARGE_ID, "NOT_CANCELABLE_PAYMENT");
        then(chargeService).should(never()).completeCancel(any(), any());
    }

    @Test
    @DisplayName("결과를 모르면 실패로 처리하지 않고 취소 중으로 돌려준다")
    void unknown() {
        given(chargeService.startCancel(MEMBER_ID, CHARGE_ID)).willReturn(TARGET);
        given(paymentGateway.cancel("payment-key", "예치금 충전 취소", IDEMPOTENCY_KEY))
            .willReturn(new PgResult.Unknown<>("timeout"));

        ChargeCancelResult result = chargeCancelService.cancel(MEMBER_ID, CHARGE_ID);

        assertThat(result).isEqualTo(new ChargeCancelResult(CHARGE_ID, "CANCELLING", 10_000L, null));
        then(chargeService).should(never()).failCancel(any(), any());
        then(chargeService).should(never()).completeCancel(any(), any());
    }

    @Test
    @DisplayName("취소 중으로 남은 충전을 같은 멱등 키로 다시 취소해, 취소됐으면 완료로 바꾼다")
    void resumeCancelled() {
        PgCancellation cancellation = new PgCancellation(LocalDateTime.now());
        given(paymentGateway.cancel("payment-key", "예치금 충전 취소", IDEMPOTENCY_KEY))
            .willReturn(new PgResult.Success<>(cancellation));

        chargeCancelService.resume(TARGET);

        then(chargeService).should().completeCancel(CHARGE_ID, cancellation);
    }

    @Test
    @DisplayName("다시 취소했는데 거절되면 예외 없이 취소 실패를 반영한다")
    void resumeRejected() {
        given(paymentGateway.cancel("payment-key", "예치금 충전 취소", IDEMPOTENCY_KEY))
            .willReturn(new PgResult.Rejected<>("NOT_CANCELABLE_PAYMENT", "취소할 수 없는 결제입니다"));

        chargeCancelService.resume(TARGET);

        then(chargeService).should().failCancel(CHARGE_ID, "NOT_CANCELABLE_PAYMENT");
    }

    @Test
    @DisplayName("다시 취소해도 결과를 모르면 아무것도 바꾸지 않는다")
    void resumeUnknown() {
        given(paymentGateway.cancel("payment-key", "예치금 충전 취소", IDEMPOTENCY_KEY))
            .willReturn(new PgResult.Unknown<>("timeout"));

        chargeCancelService.resume(TARGET);

        then(chargeService).should(never()).completeCancel(any(), any());
        then(chargeService).should(never()).failCancel(any(), any());
    }
}
