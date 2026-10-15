package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.OrderCancellationService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderCancelCommand;
import com.bbcc.kidly.boundedcontext.payment.domain.CancelledOrder;
import com.bbcc.kidly.boundedcontext.payment.domain.CancelledOrderRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Refund;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {

    private static final OrderCancelCommand COMMAND = new OrderCancelCommand(ORDER_ID, MEMBER_ID, "PAYMENT_EXPIRED");
    private static final Long REFUND_ID = 900L;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private WalletLedgerRepository walletLedgerRepository;

    @Mock
    private CancelledOrderRepository cancelledOrderRepository;

    @InjectMocks
    private OrderCancellationService orderCancellationService;

    @Test
    @DisplayName("결제가 끝난 주문이 취소되면 결제 금액을 모두 지갑에 되돌리고 원장 REFUND(ref_id = 환불 ID)와 환불 기록을 남긴다")
    void refundCompletedPayment() {
        Wallet wallet = PaymentFixture.savedWallet(0L);
        Payment payment = PaymentFixture.completedPayment();
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));
        given(refundRepository.save(any())).willAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            ReflectionTestUtils.setField(refund, "id", REFUND_ID);
            return refund;
        });

        orderCancellationService.handle(COMMAND);

        assertThat(wallet.getBalance()).isEqualTo(15_000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(15_000L);
        ArgumentCaptor<Refund> refund = ArgumentCaptor.forClass(Refund.class);
        then(refundRepository).should().save(refund.capture());
        assertThat(refund.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(refund.getValue().getCancellationId()).isNull();
        assertThat(refund.getValue().getAmount()).isEqualTo(15_000L);
        assertThat(refund.getValue().getItems()).hasSize(2);
        ArgumentCaptor<WalletLedger> ledger = ArgumentCaptor.forClass(WalletLedger.class);
        then(walletLedgerRepository).should().save(ledger.capture());
        assertThat(ledger.getValue().getWalletId()).isEqualTo(WALLET_ID);
        assertThat(ledger.getValue().getType()).isEqualTo(WalletLedgerType.REFUND);
        assertThat(ledger.getValue().getAmount()).isEqualTo(15_000L);
        assertThat(ledger.getValue().getBalanceAfter()).isEqualTo(15_000L);
        assertThat(ledger.getValue().getRefId()).isEqualTo(REFUND_ID);
        then(cancelledOrderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("결제 전에 주문이 취소되면 취소된 주문으로 기록한다")
    void rememberCancelledBeforePayment() {
        given(walletRepository.findWithLockByMemberId(MEMBER_ID))
            .willReturn(Optional.of(PaymentFixture.savedWallet(0L)));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
        given(cancelledOrderRepository.existsByOrderId(ORDER_ID)).willReturn(false);

        orderCancellationService.handle(COMMAND);

        ArgumentCaptor<CancelledOrder> cancelled = ArgumentCaptor.forClass(CancelledOrder.class);
        then(cancelledOrderRepository).should().save(cancelled.capture());
        assertThat(cancelled.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(cancelled.getValue().getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(cancelled.getValue().getReason()).isEqualTo("PAYMENT_EXPIRED");
        then(refundRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 취소된 주문으로 기록돼 있으면 다시 기록하지 않는다")
    void cancelledAlreadyRemembered() {
        given(walletRepository.findWithLockByMemberId(MEMBER_ID))
            .willReturn(Optional.of(PaymentFixture.savedWallet(0L)));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
        given(cancelledOrderRepository.existsByOrderId(ORDER_ID)).willReturn(true);

        orderCancellationService.handle(COMMAND);

        then(cancelledOrderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("결제가 실패한 주문이면 아무것도 하지 않는다")
    void failedPayment() {
        Wallet wallet = PaymentFixture.savedWallet(1_000L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(
            Payment.fail(ORDER_ID, WALLET_ID, MEMBER_ID, 15_000L, PaymentFailureCode.INSUFFICIENT_BALANCE)));

        orderCancellationService.handle(COMMAND);

        assertThat(wallet.getBalance()).isEqualTo(1_000L);
        then(refundRepository).should(never()).save(any());
        then(walletLedgerRepository).should(never()).save(any());
        then(cancelledOrderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 환불한 결제면 다시 환불하지 않는다")
    void alreadyRefunded() {
        Wallet wallet = PaymentFixture.savedWallet(15_000L);
        Payment payment = PaymentFixture.completedPayment();
        payment.getItems().forEach(item -> payment.refund(item.getOrderItemId(), item.getAmount()));
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

        orderCancellationService.handle(COMMAND);

        assertThat(wallet.getBalance()).isEqualTo(15_000L);
        then(refundRepository).should(never()).save(any());
        then(walletLedgerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("취소된 주문의 회원과 결제한 회원이 다르면 환불하지 않는다")
    void memberMismatch() {
        Wallet wallet = PaymentFixture.savedWallet(0L);
        given(walletRepository.findWithLockByMemberId(2L)).willReturn(Optional.of(wallet));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(PaymentFixture.completedPayment()));

        orderCancellationService.handle(new OrderCancelCommand(ORDER_ID, 2L, "PAYMENT_EXPIRED"));

        assertThat(wallet.getBalance()).isZero();
        then(refundRepository).should(never()).save(any());
        then(walletLedgerRepository).should(never()).save(any());
    }
}
