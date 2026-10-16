package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.PaymentStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaPaymentRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
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
 * order.payment-requested 커밋 → 예치금 차감 · 결제 · 원장 저장 → payment.completed · payment.failed.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
@RecordApplicationEvents
class OrderPaymentIntegrationTest {

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

    @Test
    @DisplayName("결제를 요청받으면 예치금을 차감하고 결제 · 원장을 남긴 뒤 payment.completed를 발행한다, 같은 이벤트가 다시 와도 한 번만 차감한다")
    void payOnce() {
        Wallet wallet = walletRepository.save(PaymentFixture.wallet(20_000L));
        OrderPaymentRequestedEvent event = paymentRequested();

        publishInTransaction(event);
        publishInTransaction(event);

        assertThat(jpaWalletRepository.findById(wallet.getId())).get()
            .extracting(Wallet::getBalance).isEqualTo(5_000L);
        assertThat(jpaPaymentRepository.findAll()).singleElement().satisfies(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(payment.getWalletId()).isEqualTo(wallet.getId());
            assertThat(payment.getAmount()).isEqualTo(15_000L);
        });
        Long paymentId = jpaPaymentRepository.findAll().get(0).getId();
        assertThat(jpaWalletLedgerRepository.findAll()).singleElement().satisfies(ledger -> {
            assertThat(ledger.getType()).isEqualTo(WalletLedgerType.PAYMENT);
            assertThat(ledger.getAmount()).isEqualTo(-15_000L);
            assertThat(ledger.getBalanceAfter()).isEqualTo(5_000L);
            assertThat(ledger.getRefId()).isEqualTo(paymentId);
        });
        assertThat(events.stream(PaymentCompletedEvent.class).toList()).singleElement().satisfies(completed -> {
            assertThat(completed.data().paymentId()).isEqualTo(paymentId);
            assertThat(completed.data().orderId()).isEqualTo(ORDER_ID);
            assertThat(completed.data().amount()).isEqualTo(15_000L);
            assertThat(completed.data().items()).hasSize(2);
        });
    }

    @Test
    @DisplayName("지갑이 없으면 잔액 0인 지갑을 만들어 잔액 부족으로 실패 결제를 남기고 payment.failed를 발행한다")
    void payWithoutWallet() {
        publishInTransaction(paymentRequested());

        assertThat(jpaWalletRepository.findAll()).singleElement().satisfies(wallet -> {
            assertThat(wallet.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(wallet.getBalance()).isZero();
        });
        assertThat(jpaPaymentRepository.findAll()).singleElement()
            .extracting(payment -> payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(jpaWalletLedgerRepository.findAll()).isEmpty();
        assertThat(events.stream(PaymentFailedEvent.class).toList()).singleElement()
            .satisfies(failed -> assertThat(failed.data().reasonCode())
                .isEqualTo(PaymentFailureCode.INSUFFICIENT_BALANCE));
    }

    private void publishInTransaction(OrderPaymentRequestedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private static OrderPaymentRequestedEvent paymentRequested() {
        return new OrderPaymentRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderPaymentRequestedEvent.Data(ORDER_ID, MEMBER_ID, 15_000L, List.of(
                new OrderPaymentRequestedEvent.Item(1000L, 10_000L),
                new OrderPaymentRequestedEvent.Item(1001L, 5_000L))));
    }
}
