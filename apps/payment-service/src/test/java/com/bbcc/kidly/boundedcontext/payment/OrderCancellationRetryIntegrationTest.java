package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetry;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetryRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.payment.in.scheduler.OrderCancellationRetryScheduler;
import com.bbcc.kidly.boundedcontext.payment.out.JpaCancelledOrderRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaOrderCancellationRetryRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaPaymentRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * order.cancelled 처리가 실패하면 결제에 다시 처리할 취소로 남고, 결제 스케줄러가 다시 처리한다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class OrderCancellationRetryIntegrationTest {

    // 취소된 주문 기록(cancelled_order.reason)은 30자까지라, 이보다 긴 사유면 결제 전 취소 처리가 DB에서 실패한다
    private static final String TOO_LONG_REASON = "R".repeat(31);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private OrderCancellationRetryScheduler orderCancellationRetryScheduler;

    @Autowired
    private OrderCancellationRetryRepository orderCancellationRetryRepository;

    @Autowired
    private JpaOrderCancellationRetryRepository jpaOrderCancellationRetryRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JpaWalletRepository jpaWalletRepository;

    @Autowired
    private JpaPaymentRepository jpaPaymentRepository;

    @Autowired
    private JpaCancelledOrderRepository jpaCancelledOrderRepository;

    @Test
    @DisplayName("취소 처리가 실패하면 처리한 것은 롤백되고, 다시 처리할 취소가 남는다")
    void rememberFailedCancellation() {
        publishInTransaction(orderCancelled(TOO_LONG_REASON));

        assertThat(jpaCancelledOrderRepository.findAll()).isEmpty();
        assertThat(jpaWalletRepository.findAll()).isEmpty();
        assertThat(jpaOrderCancellationRetryRepository.findAll()).singleElement().satisfies(retry -> {
            assertThat(retry.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(retry.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(retry.getReason()).isEqualTo(TOO_LONG_REASON);
            assertThat(retry.getAttemptCount()).isEqualTo(1);
            assertThat(retry.getLastError()).isNotBlank();
        });
    }

    @Test
    @DisplayName("다시 처리해 성공하면 결제된 주문을 환불하고 기록을 지운다")
    void retryRefunds() {
        Wallet wallet = walletRepository.save(PaymentFixture.wallet(20_000L));
        publishInTransaction(paymentRequested());
        orderCancellationRetryRepository.save(
            OrderCancellationRetry.failed(ORDER_ID, MEMBER_ID, "PAYMENT_EXPIRED", "DB 오류", LocalDateTime.now()));

        orderCancellationRetryScheduler.retryFailed();

        assertThat(jpaWalletRepository.findById(wallet.getId())).get()
            .extracting(Wallet::getBalance).isEqualTo(20_000L);
        assertThat(jpaPaymentRepository.findAll()).singleElement()
            .extracting(payment -> payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(jpaOrderCancellationRetryRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("다시 처리해도 실패하면 기록을 두고 시도 횟수를 늘린다")
    void retryFailsAgain() {
        publishInTransaction(orderCancelled(TOO_LONG_REASON));

        orderCancellationRetryScheduler.retryFailed();

        assertThat(jpaCancelledOrderRepository.findAll()).isEmpty();
        assertThat(jpaOrderCancellationRetryRepository.findAll()).singleElement()
            .extracting(OrderCancellationRetry::getAttemptCount).isEqualTo(2);
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

    private static OrderCancelledEvent orderCancelled(String reason) {
        return new OrderCancelledEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCancelledEvent.Data(ORDER_ID, MEMBER_ID, reason, List.of(
                new OrderCancelledEvent.Item(1000L, 1L, 1),
                new OrderCancelledEvent.Item(1001L, 2L, 1))));
    }
}
