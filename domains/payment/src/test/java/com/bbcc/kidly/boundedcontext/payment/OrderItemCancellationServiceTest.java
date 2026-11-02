package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.CANCELLATION_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.OrderItemCancellationService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderItemCancelCommand;
import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentItem;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Refund;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundFailureCode;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderItemCancellationServiceTest {

    private static final Long REFUND_ID = 900L;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private WalletLedgerRepository walletLedgerRepository;

    @InjectMocks
    private OrderItemCancellationService service;

    @Test
    @DisplayName("취소된 주문상품 금액만큼 지갑에 입금하고 원장 REFUND(ref_id = 환불 ID)와 취소 ID가 붙은 환불 기록을 남긴다")
    void refundCancelledItems() {
        Wallet wallet = PaymentFixture.savedWallet(0L);
        Payment payment = savedPayment();
        givenWalletAndPayment(wallet, payment);
        givenRefundSaved();

        service.handle(command(new OrderItemCancelCommand.Item(1001L, 5_000L)));

        assertThat(wallet.getBalance()).isEqualTo(5_000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(5_000L);
        Refund refund = savedRefund();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(refund.getAmount()).isEqualTo(5_000L);
        assertThat(refund.getItems()).singleElement()
            .satisfies(item -> assertThat(item.getPaymentItemId()).isEqualTo(2L));
        ArgumentCaptor<WalletLedger> ledger = ArgumentCaptor.forClass(WalletLedger.class);
        then(walletLedgerRepository).should().save(ledger.capture());
        assertThat(ledger.getValue().getWalletId()).isEqualTo(WALLET_ID);
        assertThat(ledger.getValue().getType()).isEqualTo(WalletLedgerType.REFUND);
        assertThat(ledger.getValue().getAmount()).isEqualTo(5_000L);
        assertThat(ledger.getValue().getBalanceAfter()).isEqualTo(5_000L);
        assertThat(ledger.getValue().getRefId()).isEqualTo(REFUND_ID);
    }

    @Test
    @DisplayName("마지막 몫까지 취소돼 결제 금액을 모두 돌려주면 결제는 REFUNDED다")
    void refundAllItems() {
        Wallet wallet = PaymentFixture.savedWallet(0L);
        Payment payment = savedPayment();
        givenWalletAndPayment(wallet, payment);
        givenRefundSaved();

        service.handle(command(new OrderItemCancelCommand.Item(1000L, 10_000L),
            new OrderItemCancelCommand.Item(1001L, 5_000L)));

        assertThat(wallet.getBalance()).isEqualTo(15_000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(savedRefund().getItems()).hasSize(2);
    }

    @Test
    @DisplayName("지갑을 만든 뒤 락을 잡고 나서 같은 취소의 환불 기록을 본다")
    void lockBeforeCheckingRefund() {
        Wallet wallet = PaymentFixture.savedWallet(0L);
        givenWalletAndPayment(wallet, savedPayment());
        givenRefundSaved();

        service.handle(command(new OrderItemCancelCommand.Item(1001L, 5_000L)));

        InOrder inOrder = inOrder(walletRepository, refundRepository);
        inOrder.verify(walletRepository).createIfAbsent(eq(MEMBER_ID), any(LocalDateTime.class));
        inOrder.verify(walletRepository).findWithLockByMemberId(MEMBER_ID);
        inOrder.verify(refundRepository).existsByCancellationId(CANCELLATION_ID);
    }

    @Test
    @DisplayName("같은 취소의 환불 기록이 있으면 다시 환불하지 않는다")
    void sameCancellationOnce() {
        Wallet wallet = PaymentFixture.savedWallet(0L);
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(refundRepository.existsByCancellationId(CANCELLATION_ID)).willReturn(true);

        service.handle(command(new OrderItemCancelCommand.Item(1001L, 5_000L)));

        assertThat(wallet.getBalance()).isZero();
        then(paymentRepository).should(never()).findByOrderId(any());
        then(refundRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("남은 금액보다 많이 환불하라면 입금 없이 실패한 환불(REFUND_AMOUNT_EXCEEDED)로 남기고 결제는 그대로 둔다")
    void failWhenAmountExceeded() {
        Wallet wallet = PaymentFixture.savedWallet(0L);
        Payment payment = savedPayment();
        givenWalletAndPayment(wallet, payment);
        givenRefundSaved();

        service.handle(command(new OrderItemCancelCommand.Item(1000L, 10_000L),
            new OrderItemCancelCommand.Item(1001L, 5_001L)));

        assertFailed(wallet, payment, RefundFailureCode.REFUND_AMOUNT_EXCEEDED, 15_001L);
    }

    @Test
    @DisplayName("결제에 없는 주문상품이면 실패한 환불(PAYMENT_ITEM_NOT_FOUND)로 남긴다")
    void failWhenItemMissing() {
        Wallet wallet = PaymentFixture.savedWallet(0L);
        Payment payment = savedPayment();
        givenWalletAndPayment(wallet, payment);
        givenRefundSaved();

        service.handle(command(new OrderItemCancelCommand.Item(9999L, 5_000L)));

        assertFailed(wallet, payment, RefundFailureCode.PAYMENT_ITEM_NOT_FOUND, 5_000L);
    }

    @Test
    @DisplayName("이미 모두 환불된 결제면 실패한 환불(INVALID_PAYMENT_STATUS)로 남긴다")
    void failWhenAlreadyRefunded() {
        Wallet wallet = PaymentFixture.savedWallet(0L);
        Payment payment = savedPayment();
        payment.refund(1000L, 10_000L);
        payment.refund(1001L, 5_000L);
        givenWalletAndPayment(wallet, payment);
        givenRefundSaved();

        service.handle(command(new OrderItemCancelCommand.Item(1001L, 5_000L)));

        ArgumentCaptor<Refund> refund = ArgumentCaptor.forClass(Refund.class);
        then(refundRepository).should().save(refund.capture());
        assertThat(refund.getValue().getFailureCode()).isEqualTo(RefundFailureCode.INVALID_PAYMENT_STATUS);
        assertThat(wallet.getBalance()).isZero();
        then(walletLedgerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("결제한 회원과 취소의 회원이 다르면 환불하지 않는다")
    void skipWhenMemberDiffers() {
        Wallet wallet = PaymentFixture.savedWallet(0L);
        Payment payment = Payment.complete(ORDER_ID, WALLET_ID, 2L, List.of(
            PaymentItem.of(1001L, 5_000L)), PaymentFixture.NOW);
        givenWalletAndPayment(wallet, payment);

        service.handle(command(new OrderItemCancelCommand.Item(1001L, 5_000L)));

        assertThat(wallet.getBalance()).isZero();
        then(refundRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("결제가 없는 주문의 부분 취소는 다시 해도 같아 IllegalStateException을 던진다(재시도 없이 DLT)")
    void throwWhenPaymentMissing() {
        given(walletRepository.findWithLockByMemberId(MEMBER_ID))
            .willReturn(Optional.of(PaymentFixture.savedWallet(0L)));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(command(new OrderItemCancelCommand.Item(1001L, 5_000L))))
            .isInstanceOf(IllegalStateException.class);
        then(refundRepository).should(never()).save(any());
    }

    private void assertFailed(Wallet wallet, Payment payment, RefundFailureCode failureCode, long amount) {
        Refund refund = savedRefund();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureCode()).isEqualTo(failureCode);
        assertThat(refund.getCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getAmount()).isEqualTo(amount);
        assertThat(wallet.getBalance()).isZero();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getRefundedAmount()).isZero();
        assertThat(payment.getItems()).allSatisfy(item -> assertThat(item.getRefundedAmount()).isZero());
        then(walletLedgerRepository).should(never()).save(any());
    }

    private void givenWalletAndPayment(Wallet wallet, Payment payment) {
        given(walletRepository.findWithLockByMemberId(MEMBER_ID)).willReturn(Optional.of(wallet));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));
    }

    private void givenRefundSaved() {
        given(refundRepository.save(any())).willAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            ReflectionTestUtils.setField(refund, "id", REFUND_ID);
            return refund;
        });
    }

    private Refund savedRefund() {
        ArgumentCaptor<Refund> refund = ArgumentCaptor.forClass(Refund.class);
        then(refundRepository).should().save(refund.capture());
        return refund.getValue();
    }

    // 저장된 결제처럼 결제 항목 ID가 1(주문상품 1000, 10,000원) · 2(주문상품 1001, 5,000원)
    private static Payment savedPayment() {
        Payment payment = PaymentFixture.completedPayment();
        ReflectionTestUtils.setField(payment, "id", 500L);
        ReflectionTestUtils.setField(payment.getItems().get(0), "id", 1L);
        ReflectionTestUtils.setField(payment.getItems().get(1), "id", 2L);
        return payment;
    }

    private static OrderItemCancelCommand command(OrderItemCancelCommand.Item... items) {
        return new OrderItemCancelCommand(ORDER_ID, MEMBER_ID, CANCELLATION_ID, "OUT_OF_STOCK", List.of(items));
    }
}
