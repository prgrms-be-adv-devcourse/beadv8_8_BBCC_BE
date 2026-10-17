package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.payment.app.ChargeCancelService;
import com.bbcc.kidly.boundedcontext.payment.app.ChargeConfirmService;
import com.bbcc.kidly.boundedcontext.payment.app.ChargeReconcileService;
import com.bbcc.kidly.boundedcontext.payment.app.ChargeService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeRequestResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ConfirmChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaPaymentRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 예치금 · 충전 · 결제 상태가 바뀌면 커밋된 뒤 행동 로그(action.payment)가 한 번씩 남는지 확인한다.
 */
@IntegrationTest
class PaymentActionLogIntegrationTest {

    private static final String PAYMENT_KEY = "tgen_20261017120000abcdefghij";
    private static final Long SETTLEMENT_ID = 900L;
    private static final String ACTION_KEY = "action=";

    private ActionLogCapture capture;

    @Autowired
    private ChargeService chargeService;

    @Autowired
    private ChargeConfirmService chargeConfirmService;

    @Autowired
    private ChargeCancelService chargeCancelService;

    @Autowired
    private ChargeReconcileService chargeReconcileService;

    @Autowired
    private ChargeRepository chargeRepository;

    @Autowired
    private FakePaymentGateway fakePaymentGateway;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JpaPaymentRepository jpaPaymentRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // 컨텍스트가 뜰 때 logback 설정을 다시 읽어 붙여 둔 appender가 떨어지므로, 컨텍스트가 뜬 뒤에 붙인다
    @BeforeEach
    void setUp() {
        capture = ActionLogCapture.start();
    }

    @AfterEach
    void tearDown() {
        capture.close();
        fakePaymentGateway.reset();
    }

    @Test
    @DisplayName("충전 요청 · 승인 시작 · 승인 · 취소를 차례로 남기고, 승인에는 금액과 바뀐 잔액을 담는다")
    void chargeLifecycle() {
        Wallet wallet = walletRepository.save(PaymentFixture.wallet(5_000L));
        ChargeRequestResult requested = chargeService.request(MEMBER_ID, new ChargeRequest(10_000L));
        chargeConfirmService.confirm(MEMBER_ID, new ConfirmChargeRequest(PAYMENT_KEY, requested.orderId(), 10_000L));
        chargeCancelService.cancel(MEMBER_ID, requested.chargeId());

        assertThat(actions()).containsExactly("CHARGE_REQUESTED", "CHARGE_CONFIRM_STARTED", "CHARGE_CONFIRMED",
            "CHARGE_CANCEL_REQUESTED", "CHARGE_CANCELLED");
        assertThat(capture.messages().get(1)).isEqualTo(
            "충전 승인 시작 action=CHARGE_CONFIRM_STARTED memberId=%d walletId=%d chargeId=%d amount=10000"
                .formatted(MEMBER_ID, wallet.getId(), requested.chargeId()));
        assertThat(capture.messages().get(2)).isEqualTo(
            "충전 승인 action=CHARGE_CONFIRMED memberId=%d walletId=%d chargeId=%d amount=10000 balance=15000"
                .formatted(MEMBER_ID, wallet.getId(), requested.chargeId()));
    }

    @Test
    @DisplayName("토스가 승인을 거절하면 충전 실패를 거절 코드와 함께 남긴다")
    void chargeRejected() {
        walletRepository.save(PaymentFixture.wallet(0L));
        ChargeRequestResult requested = chargeService.request(MEMBER_ID, new ChargeRequest(10_000L));

        assertThatThrownBy(() -> chargeConfirmService.confirm(MEMBER_ID,
            new ConfirmChargeRequest(FakePaymentGateway.REJECTED_KEY, requested.orderId(), 10_000L)))
            .isInstanceOf(BusinessException.class);

        assertThat(actions()).containsExactly("CHARGE_REQUESTED", "CHARGE_CONFIRM_STARTED", "CHARGE_FAILED");
        assertThat(capture.messages().get(2))
            .contains("chargeId=" + requested.chargeId(), "failureCode=" + FakePaymentGateway.REJECTED_CODE);
    }

    @Test
    @DisplayName("토스가 충전 취소를 거절하면 다시 넣은 금액과 잔액을 남긴다")
    void chargeCancelRejected() {
        walletRepository.save(PaymentFixture.wallet(0L));
        ChargeRequestResult requested = chargeService.request(MEMBER_ID, new ChargeRequest(10_000L));
        chargeConfirmService.confirm(MEMBER_ID,
            new ConfirmChargeRequest(FakePaymentGateway.CANCEL_REJECTED_KEY, requested.orderId(), 10_000L));

        assertThatThrownBy(() -> chargeCancelService.cancel(MEMBER_ID, requested.chargeId()))
            .isInstanceOf(BusinessException.class);

        assertThat(actions()).endsWith("CHARGE_CANCEL_REQUESTED", "CHARGE_CANCEL_FAILED");
        assertThat(capture.messages().getLast()).contains("chargeId=" + requested.chargeId(),
            "failureCode=" + FakePaymentGateway.CANCEL_REJECTED_CODE, "amount=10000", "balance=10000");
    }

    @Test
    @DisplayName("만료 시각이 지난 대기 충전을 만료로 바꾸면 지갑과 충전 ID를 남긴다")
    void chargeExpired() {
        Wallet wallet = walletRepository.save(PaymentFixture.wallet(0L));
        Charge charge = chargeRepository.save(Charge.request(wallet.getId(), UUID.randomUUID().toString(), 10_000L,
            LocalDateTime.now().minusMinutes(1)));

        chargeReconcileService.expire(charge.getId());

        assertThat(capture.messages()).containsExactly(
            "충전 만료 action=CHARGE_EXPIRED walletId=%d chargeId=%d".formatted(wallet.getId(), charge.getId()));
    }

    @Test
    @DisplayName("토스에서 직접 취소된 충전을 반영하면 회수한 금액 · 못 회수한 금액 · 바뀐 잔액을 남긴다")
    void chargeCancelledExternally() {
        Wallet wallet = walletRepository.save(PaymentFixture.wallet(5_000L));
        ChargeRequestResult requested = chargeService.request(MEMBER_ID, new ChargeRequest(10_000L));
        chargeConfirmService.confirm(MEMBER_ID, new ConfirmChargeRequest(PAYMENT_KEY, requested.orderId(), 10_000L));
        fakePaymentGateway.willFindCancelled(requested.orderId());

        chargeReconcileService.reconcileExternalCancel(requested.orderId());

        assertThat(actions()).endsWith("CHARGE_CONFIRMED", "CHARGE_CANCELLED_EXTERNALLY");
        assertThat(capture.messages().getLast()).isEqualTo(
            ("토스 직접 취소 반영 action=CHARGE_CANCELLED_EXTERNALLY memberId=%d walletId=%d chargeId=%d amount=10000 "
                + "recoveredAmount=10000 shortfallAmount=0 balance=5000")
                .formatted(MEMBER_ID, wallet.getId(), requested.chargeId()));
    }

    @Test
    @DisplayName("주문 결제와 취소 환불을 한 번씩 남긴다. 같은 취소가 다시 와도 환불 로그는 하나다")
    void orderPaidThenRefunded() {
        Wallet wallet = walletRepository.save(PaymentFixture.wallet(20_000L));

        publishInTransaction(paymentRequested());
        publishInTransaction(orderCancelled());
        publishInTransaction(orderCancelled());

        Long paymentId = jpaPaymentRepository.findAll().getFirst().getId();
        assertThat(actions()).containsExactly("ORDER_PAID", "ORDER_REFUNDED");
        assertThat(capture.messages().getFirst()).isEqualTo(
            "주문 결제 완료 action=ORDER_PAID memberId=%d walletId=%d orderId=%d paymentId=%d amount=15000 balance=5000"
                .formatted(MEMBER_ID, wallet.getId(), ORDER_ID, paymentId));
        assertThat(capture.messages().get(1)).contains("paymentId=" + paymentId, "amount=15000", "balance=20000");
    }

    @Test
    @DisplayName("결제 전에 취소된 주문은 취소 기록을 남기고, 뒤에 온 결제 요청은 결제 로그를 남기지 않는다")
    void cancelledBeforePayment() {
        walletRepository.save(PaymentFixture.wallet(20_000L));

        publishInTransaction(orderCancelled());
        publishInTransaction(paymentRequested());

        assertThat(capture.messages()).containsExactly(
            "결제 전 주문 취소 기록 action=ORDER_CANCEL_RECORDED memberId=%d orderId=%d reason=PAYMENT_EXPIRED"
                .formatted(MEMBER_ID, ORDER_ID));
    }

    @Test
    @DisplayName("잔액이 모자라면 주문 결제 실패를 사유와 함께 남긴다")
    void orderPaymentFailed() {
        walletRepository.save(PaymentFixture.wallet(1_000L));

        publishInTransaction(paymentRequested());

        assertThat(actions()).containsExactly("ORDER_PAYMENT_FAILED");
        assertThat(capture.messages().getFirst()).contains("orderId=" + ORDER_ID, "reasonCode=INSUFFICIENT_BALANCE");
    }

    @Test
    @DisplayName("결제 요청 금액이 주문상품 금액의 합과 다르면 결제 ID 없이 주문 결제 실패를 남긴다")
    void orderPaymentAmountMismatch() {
        walletRepository.save(PaymentFixture.wallet(20_000L));

        publishInTransaction(new OrderPaymentRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderPaymentRequestedEvent.Data(ORDER_ID, MEMBER_ID, 15_000L, List.of(
                new OrderPaymentRequestedEvent.Item(1000L, 10_000L),
                new OrderPaymentRequestedEvent.Item(1001L, 4_000L)))));

        assertThat(capture.messages()).containsExactly(
            ("주문 결제 실패 action=ORDER_PAYMENT_FAILED memberId=%d orderId=%d paymentId=null amount=15000 "
                + "reasonCode=AMOUNT_MISMATCH")
                .formatted(MEMBER_ID, ORDER_ID));
    }

    @Test
    @DisplayName("정산 지급은 입금한 한 번만 남긴다")
    void payoutOnce() {
        walletRepository.save(PaymentFixture.wallet(5_000L));
        SettlementPayoutRequestedEvent event = payoutRequested();

        publishInTransaction(event);
        publishInTransaction(event);

        assertThat(actions()).containsExactly("PAYOUT_COMPLETED");
        assertThat(capture.messages().getFirst())
            .contains("memberId=" + MEMBER_ID, "settlementId=" + SETTLEMENT_ID, "amount=270000", "balance=275000");
    }

    private List<String> actions() {
        return capture.messages().stream()
            .map(message -> message.substring(message.indexOf(ACTION_KEY) + ACTION_KEY.length()).split(" ")[0])
            .toList();
    }

    private void publishInTransaction(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private static OrderPaymentRequestedEvent paymentRequested() {
        return new OrderPaymentRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderPaymentRequestedEvent.Data(ORDER_ID, MEMBER_ID, 15_000L, List.of(
                new OrderPaymentRequestedEvent.Item(1000L, 10_000L),
                new OrderPaymentRequestedEvent.Item(1001L, 5_000L))));
    }

    private static OrderCancelledEvent orderCancelled() {
        return new OrderCancelledEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCancelledEvent.Data(ORDER_ID, MEMBER_ID, "PAYMENT_EXPIRED", List.of(
                new OrderCancelledEvent.Item(1000L, 1L, 1),
                new OrderCancelledEvent.Item(1001L, 2L, 1))));
    }

    private static SettlementPayoutRequestedEvent payoutRequested() {
        return new SettlementPayoutRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SettlementPayoutRequestedEvent.Data(
                SETTLEMENT_ID, MEMBER_ID, 270_000L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }
}
