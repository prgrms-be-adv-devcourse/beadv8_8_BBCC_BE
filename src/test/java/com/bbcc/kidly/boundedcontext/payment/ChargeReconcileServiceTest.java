package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.CHARGE_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.TOSS_ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.ChargeProperties;
import com.bbcc.kidly.boundedcontext.payment.app.ChargeReconcileService;
import com.bbcc.kidly.boundedcontext.payment.app.ChargeService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeCancelTarget;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeReconcileTarget;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChargeReconcileServiceTest {

    private static final ChargeReconcileTarget TARGET = new ChargeReconcileTarget(CHARGE_ID, TOSS_ORDER_ID, 10_000L);

    @Mock
    private ChargeRepository chargeRepository;

    @Mock
    private ChargeService chargeService;

    @Mock
    private PaymentGateway paymentGateway;

    private ChargeReconcileService chargeReconcileService;

    @BeforeEach
    void setUp() {
        chargeReconcileService = new ChargeReconcileService(
            chargeRepository, chargeService, paymentGateway,
            new ChargeProperties(Duration.ofMinutes(5), Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("5분 넘게 승인 중인 충전을 찾는다")
    void findStaleConfirming() {
        Charge charge = PaymentFixture.savedCharge(10_000L);
        given(chargeRepository.findAllByStatusAndUpdatedAtBefore(eq(ChargeStatus.CONFIRMING), any()))
            .willReturn(List.of(charge));

        List<ChargeReconcileTarget> targets = chargeReconcileService.findStaleConfirming();

        assertThat(targets).containsExactly(TARGET);
        ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        then(chargeRepository).should()
            .findAllByStatusAndUpdatedAtBefore(eq(ChargeStatus.CONFIRMING), before.capture());
        assertThat(before.getValue()).isCloseTo(LocalDateTime.now().minusMinutes(5), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("5분 넘게 취소 중인 충전을 마지막 취소 요청으로 찾는다")
    void findStaleCancelling() {
        Charge charge = PaymentFixture.savedCancellingCharge(10_000L);
        given(chargeRepository.findAllByStatusAndUpdatedAtBefore(eq(ChargeStatus.CANCELLING), any()))
            .willReturn(List.of(charge));

        List<ChargeCancelTarget> targets = chargeReconcileService.findStaleCancelling();

        assertThat(targets).containsExactly(new ChargeCancelTarget(
            CHARGE_ID, PaymentFixture.CANCELLATION_ID, "encrypted-payment-key", "예치금 충전 취소", 10_000L));
        ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        then(chargeRepository).should()
            .findAllByStatusAndUpdatedAtBefore(eq(ChargeStatus.CANCELLING), before.capture());
        assertThat(before.getValue()).isCloseTo(LocalDateTime.now().minusMinutes(5), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("토스에서 승인됐으면 승인을 반영한다")
    void reconcileApproved() {
        PgApproval approval = new PgApproval("payment-key", 10_000L, LocalDateTime.now());
        given(paymentGateway.findByOrderId(TOSS_ORDER_ID)).willReturn(new PgResult.Success<>(approval));

        chargeReconcileService.reconcile(TARGET);

        then(chargeService).should().approve(CHARGE_ID, approval);
    }

    @Test
    @DisplayName("승인 금액이 충전 금액과 다르면 반영하지 않는다")
    void reconcileAmountMismatch() {
        given(paymentGateway.findByOrderId(TOSS_ORDER_ID)).willReturn(
            new PgResult.Success<>(new PgApproval("payment-key", 9_000L, LocalDateTime.now())));

        chargeReconcileService.reconcile(TARGET);

        then(chargeService).should(never()).approve(any(), any());
        then(chargeService).should(never()).fail(any(), any());
    }

    @Test
    @DisplayName("토스에서 승인되지 않고 끝났으면 실패로 바꾼다")
    void reconcileRejected() {
        given(paymentGateway.findByOrderId(TOSS_ORDER_ID)).willReturn(new PgResult.Rejected<>("ABORTED", "취소됨"));

        chargeReconcileService.reconcile(TARGET);

        then(chargeService).should().fail(CHARGE_ID, "ABORTED");
    }

    @Test
    @DisplayName("아직 결과를 모르면 아무것도 바꾸지 않는다")
    void reconcileUnknown() {
        given(paymentGateway.findByOrderId(TOSS_ORDER_ID)).willReturn(new PgResult.Unknown<>("timeout"));

        chargeReconcileService.reconcile(TARGET);

        then(chargeService).should(never()).approve(any(), any());
        then(chargeService).should(never()).fail(any(), any());
    }

    @Test
    @DisplayName("만료 시각이 지난 대기 중 충전을 만료로 바꾼다")
    void expire() {
        Charge charge = PaymentFixture.savedCharge(10_000L);
        ReflectionTestUtils.setField(charge, "expiresAt", LocalDateTime.now().minusMinutes(1));
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(charge));

        chargeReconcileService.expire(CHARGE_ID);

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.EXPIRED);
    }

    @Test
    @DisplayName("그사이 승인이 시작됐거나 아직 만료 전이면 그대로 둔다")
    void expireSkipsChangedCharge() {
        Charge confirming = PaymentFixture.savedCharge(10_000L);
        confirming.startConfirming(10_000L, LocalDateTime.now());
        ReflectionTestUtils.setField(confirming, "expiresAt", LocalDateTime.now().minusMinutes(1));
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(confirming));

        chargeReconcileService.expire(CHARGE_ID);

        assertThat(confirming.getStatus()).isEqualTo(ChargeStatus.CONFIRMING);

        Charge notYet = PaymentFixture.savedCharge(10_000L);
        given(chargeRepository.findWithLockById(CHARGE_ID)).willReturn(Optional.of(notYet));

        chargeReconcileService.expire(CHARGE_ID);

        assertThat(notYet.getStatus()).isEqualTo(ChargeStatus.PENDING);
    }
}
