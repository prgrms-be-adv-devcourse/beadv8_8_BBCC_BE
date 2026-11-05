package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.PaymentStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaCancelledOrderRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaPaymentRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaRefundRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * order.cancelled 커밋 → 결제된 주문이면 예치금 환불, 결제 전이면 취소된 주문 기록 → 그 뒤 결제 요청은 차감하지 않음.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
@RecordApplicationEvents
class OrderCancelRefundIntegrationTest {

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JpaWalletRepository jpaWalletRepository;

    @Autowired
    private JpaPaymentRepository jpaPaymentRepository;

    @Autowired
    private JpaWalletLedgerRepository jpaWalletLedgerRepository;

    @Autowired
    private JpaRefundRepository jpaRefundRepository;

    @Autowired
    private JpaCancelledOrderRepository jpaCancelledOrderRepository;

    @Test
    @DisplayName("결제가 끝난 주문이 취소되면 예치금을 되돌리고 원장 REFUND를 남긴다, 취소가 다시 와도 한 번만 환불한다")
    void refundOnce() {
        Wallet wallet = walletRepository.save(PaymentFixture.wallet(20_000L));
        publishInTransaction(paymentRequested());

        publishInTransaction(orderCancelled());
        publishInTransaction(orderCancelled());

        assertThat(jpaWalletRepository.findById(wallet.getId())).get()
            .extracting(Wallet::getBalance).isEqualTo(20_000L);
        assertThat(jpaPaymentRepository.findAll()).singleElement().satisfies(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment.getRefundedAmount()).isEqualTo(15_000L);
        });
        Long refundId = jpaRefundRepository.findAll().getFirst().getId();
        assertThat(jpaRefundRepository.findAll()).singleElement()
            .satisfies(refund -> assertThat(refund.getAmount()).isEqualTo(15_000L));
        assertThat(jpaWalletLedgerRepository.findAll()).extracting(WalletLedger::getType)
            .containsExactlyInAnyOrder(WalletLedgerType.PAYMENT, WalletLedgerType.REFUND);
        assertThat(jpaWalletLedgerRepository.findAll())
            .filteredOn(ledger -> ledger.getType() == WalletLedgerType.REFUND)
            .singleElement().satisfies(ledger -> {
                assertThat(ledger.getAmount()).isEqualTo(15_000L);
                assertThat(ledger.getBalanceAfter()).isEqualTo(20_000L);
                assertThat(ledger.getRefId()).isEqualTo(refundId);
            });
    }

    @Test
    @DisplayName("결제 전에 주문이 취소되면 그 뒤에 온 결제 요청은 차감하지 않고 아무것도 발행하지 않는다")
    void rejectPaymentAfterCancel() {
        Wallet wallet = walletRepository.save(PaymentFixture.wallet(20_000L));

        publishInTransaction(orderCancelled());
        publishInTransaction(paymentRequested());

        assertThat(jpaCancelledOrderRepository.findAll()).singleElement()
            .satisfies(cancelled -> assertThat(cancelled.getOrderId()).isEqualTo(ORDER_ID));
        assertThat(jpaWalletRepository.findById(wallet.getId())).get()
            .extracting(Wallet::getBalance).isEqualTo(20_000L);
        assertThat(jpaPaymentRepository.findAll()).isEmpty();
        assertThat(jpaWalletLedgerRepository.findAll()).isEmpty();
        assertThat(events.stream(PaymentCompletedEvent.class).toList()).isEmpty();
        assertThat(events.stream(PaymentFailedEvent.class).toList()).isEmpty();
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
}
