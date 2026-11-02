package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.OrderPaymentService;
import com.bbcc.kidly.boundedcontext.payment.domain.CancelledOrderRepository;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PayOrderCommand;
import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceTest {

    private static final PayOrderCommand COMMAND = new PayOrderCommand(ORDER_ID, MEMBER_ID, 15_000L, List.of(
        new PayOrderCommand.Item(1000L, 10_000L),
        new PayOrderCommand.Item(1001L, 5_000L)));

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WalletLedgerRepository walletLedgerRepository;

    @Mock
    private CancelledOrderRepository cancelledOrderRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private OrderPaymentService orderPaymentService;

    @Test
    @DisplayName("잔액이 충분하면 차감하고 결제 · 원장을 저장한 뒤 payment.completed를 발행한다")
    void pay() {
        Wallet wallet = PaymentFixture.wallet(20_000L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(paymentRepository.save(any())).willAnswer(returnsFirstArg());

        orderPaymentService.pay(COMMAND);

        assertThat(wallet.getBalance()).isEqualTo(5_000L);
        Payment payment = savedPayment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getAmount()).isEqualTo(15_000L);
        assertThat(payment.getItems()).hasSize(2);

        ArgumentCaptor<WalletLedger> ledger = ArgumentCaptor.forClass(WalletLedger.class);
        then(walletLedgerRepository).should().save(ledger.capture());
        assertThat(ledger.getValue().getType()).isEqualTo(WalletLedgerType.PAYMENT);
        assertThat(ledger.getValue().getAmount()).isEqualTo(-15_000L);
        assertThat(ledger.getValue().getBalanceAfter()).isEqualTo(5_000L);

        ArgumentCaptor<PaymentCompletedEvent> event = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        then(eventPublisher).should().publish(event.capture());
        assertThat(event.getValue().data().orderId()).isEqualTo(ORDER_ID);
        assertThat(event.getValue().data().memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.getValue().data().amount()).isEqualTo(15_000L);
        assertThat(event.getValue().data().items()).containsExactly(
            new PaymentCompletedEvent.Item(1000L, 10_000L),
            new PaymentCompletedEvent.Item(1001L, 5_000L));
        assertThat(event.getValue().data().paidAt()).isEqualTo(payment.getPaidAt());
    }

    @Test
    @DisplayName("잔액이 부족하면 잔액 · 원장은 그대로 두고 실패 결제를 저장한 뒤 payment.failed를 발행한다")
    void insufficientBalance() {
        Wallet wallet = PaymentFixture.wallet(14_999L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(paymentRepository.save(any())).willAnswer(returnsFirstArg());

        orderPaymentService.pay(COMMAND);

        assertThat(wallet.getBalance()).isEqualTo(14_999L);
        assertThat(savedPayment().getFailureCode()).isEqualTo(PaymentFailureCode.INSUFFICIENT_BALANCE);
        then(walletLedgerRepository).should(never()).save(any());
        assertFailedEvent(PaymentFailureCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("지갑이 없으면 잔액 0인 지갑을 만든 뒤 락으로 읽어 결제한다")
    void createWalletBeforePay() {
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(PaymentFixture.wallet(0L)));
        given(paymentRepository.save(any())).willAnswer(returnsFirstArg());

        orderPaymentService.pay(COMMAND);

        InOrder inOrder = inOrder(walletRepository);
        inOrder.verify(walletRepository).createIfAbsent(eq(MEMBER_ID), any(LocalDateTime.class));
        inOrder.verify(walletRepository).findWithLockByMemberId(MEMBER_ID);
        assertThat(savedPayment().getFailureCode()).isEqualTo(PaymentFailureCode.INSUFFICIENT_BALANCE);
        assertFailedEvent(PaymentFailureCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("요청 금액이 주문상품 금액의 합과 다르면 지갑을 건드리지 않고 payment.failed만 발행한다")
    void amountMismatch() {
        PayOrderCommand mismatched = new PayOrderCommand(ORDER_ID, MEMBER_ID, 14_000L, COMMAND.items());

        orderPaymentService.pay(mismatched);

        then(walletRepository).should(never()).findWithLockByMemberId(any());
        then(paymentRepository).should(never()).save(any());
        PaymentFailedEvent event = assertFailedEvent(PaymentFailureCode.AMOUNT_MISMATCH, 14_000L);
        assertThat(event.data().paymentId()).isNull();
    }

    @Test
    @DisplayName("이미 결제한 주문이면 아무것도 하지 않는다")
    void alreadyPaid() {
        given(paymentRepository.existsByOrderId(ORDER_ID)).willReturn(true);

        orderPaymentService.pay(COMMAND);

        then(walletRepository).should(never()).findWithLockByMemberId(any());
        then(paymentRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publish(any());
    }

    private Payment savedPayment() {
        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        then(paymentRepository).should().save(payment.capture());
        return payment.getValue();
    }

    private PaymentFailedEvent assertFailedEvent(PaymentFailureCode reasonCode) {
        return assertFailedEvent(reasonCode, 15_000L);
    }

    private PaymentFailedEvent assertFailedEvent(PaymentFailureCode reasonCode, long requestedAmount) {
        ArgumentCaptor<PaymentFailedEvent> event = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        then(eventPublisher).should().publish(event.capture());
        assertThat(event.getValue().data().orderId()).isEqualTo(ORDER_ID);
        assertThat(event.getValue().data().memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.getValue().data().requestedAmount()).isEqualTo(requestedAmount);
        assertThat(event.getValue().data().reasonCode()).isEqualTo(reasonCode);
        assertThat(event.getValue().data().failedAt()).isNotNull();
        return event.getValue();
    }

    @Test
    @DisplayName("탈퇴로 동결된 지갑이면 차감 · 결제 저장 없이 payment.failed(WALLET_NOT_FOUND)를 발행한다")
    void frozenWallet() {
        Wallet wallet = PaymentFixture.savedWallet(20_000L);
        wallet.freeze(LocalDateTime.now());
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));

        orderPaymentService.pay(COMMAND);

        assertThat(wallet.getBalance()).isEqualTo(20_000L);
        then(paymentRepository).should(never()).save(any());
        then(walletLedgerRepository).should(never()).save(any());
        assertThat(assertFailedEvent(PaymentFailureCode.WALLET_NOT_FOUND).data().paymentId()).isNull();
    }

    @Test
    @DisplayName("이미 취소된 주문의 결제 요청이면 차감하지 않고 아무것도 발행하지 않는다")
    void cancelledOrder() {
        Wallet wallet = PaymentFixture.savedWallet(20_000L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(cancelledOrderRepository.existsByOrderId(ORDER_ID)).willReturn(true);

        orderPaymentService.pay(COMMAND);

        assertThat(wallet.getBalance()).isEqualTo(20_000L);
        then(paymentRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publish(any());
    }
}
