package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.CHARGE_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.TOSS_ORDER_ID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.ChargeProperties;
import com.bbcc.kidly.boundedcontext.payment.app.ChargeService;
import com.bbcc.kidly.boundedcontext.payment.app.StuckChargeService;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StuckChargeServiceTest {

    private static final Long ADMIN_ID = 3L;

    @Mock
    private ChargeRepository chargeRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private ChargeService chargeService;

    @Mock
    private PaymentGateway paymentGateway;

    private StuckChargeService stuckChargeService;

    @BeforeEach
    void setUp() {
        stuckChargeService = new StuckChargeService(chargeRepository, walletRepository, chargeService, paymentGateway,
            new ChargeProperties(Duration.ofMinutes(5), Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("토스 승인 금액이 다르면 토스가 승인한 금액을 남기고 끝낸다")
    void closeOnAmountMismatch() {
        givenStuckCharge(10_000L);
        given(paymentGateway.findByOrderId(TOSS_ORDER_ID)).willReturn(approved(7_000L));

        stuckChargeService.close(CHARGE_ID, ADMIN_ID);

        then(chargeService).should().closeStuck(eq(CHARGE_ID), any(), eq("APPROVED_AMOUNT=7000"), eq(ADMIN_ID));
    }

    @Test
    @DisplayName("토스에서 부분 취소됐으면 그 상태를 남기고 끝낸다")
    void closeOnPartialCancel() {
        givenStuckCharge(10_000L);
        given(paymentGateway.findByOrderId(TOSS_ORDER_ID))
            .willReturn(new PgResult.Rejected<>(PaymentGateway.PARTIAL_CANCELED, "부분 취소"));

        stuckChargeService.close(CHARGE_ID, ADMIN_ID);

        then(chargeService).should()
            .closeStuck(eq(CHARGE_ID), any(), eq(PaymentGateway.PARTIAL_CANCELED), eq(ADMIN_ID));
    }

    @Test
    @DisplayName("토스가 충전 금액대로 승인했으면 스케줄러가 입금할 충전이라 409로 거절한다")
    void rejectApprovedAtPg() {
        givenStuckCharge(10_000L);
        given(paymentGateway.findByOrderId(TOSS_ORDER_ID)).willReturn(approved(10_000L));

        assertThatThrownBy(() -> stuckChargeService.close(CHARGE_ID, ADMIN_ID))
            .extracting("errorCode").isEqualTo(PaymentErrorCode.CHARGE_APPROVED_AT_PG);
        then(chargeService).should(never()).closeStuck(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("토스 결과를 모르면 돈이 들어올 수 있어 503으로 거절한다")
    void rejectUnknown() {
        givenStuckCharge(10_000L);
        given(paymentGateway.findByOrderId(TOSS_ORDER_ID)).willReturn(new PgResult.Unknown<>("timeout"));

        assertThatThrownBy(() -> stuckChargeService.close(CHARGE_ID, ADMIN_ID))
            .extracting("errorCode").isEqualTo(PaymentErrorCode.PG_RESULT_UNKNOWN);
        then(chargeService).should(never()).closeStuck(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("멈춘 충전이 아니면 토스를 조회하지 않고 409로 거절한다")
    void rejectNotStuckWithoutPgLookup() {
        Charge charge = PaymentFixture.savedCharge(10_000L);
        charge.startConfirming(10_000L, LocalDateTime.now());
        ReflectionTestUtils.setField(charge, "updatedAt", LocalDateTime.now());
        given(chargeRepository.findById(CHARGE_ID)).willReturn(Optional.of(charge));

        assertThatThrownBy(() -> stuckChargeService.close(CHARGE_ID, ADMIN_ID))
            .extracting("errorCode").isEqualTo(PaymentErrorCode.CHARGE_NOT_STUCK);
        then(paymentGateway).should(never()).findByOrderId(any());
    }

    private void givenStuckCharge(long amount) {
        Charge charge = PaymentFixture.savedCharge(amount);
        charge.startConfirming(amount, LocalDateTime.now());
        ReflectionTestUtils.setField(charge, "updatedAt", LocalDateTime.now().minusMinutes(10));
        given(chargeRepository.findById(CHARGE_ID)).willReturn(Optional.of(charge));
    }

    private static PgResult<PgApproval> approved(long amount) {
        return new PgResult.Success<>(new PgApproval("payment-key", amount, LocalDateTime.now()));
    }
}
