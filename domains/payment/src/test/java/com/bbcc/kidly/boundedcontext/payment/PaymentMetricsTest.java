package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentMetrics;
import com.bbcc.kidly.boundedcontext.payment.app.PaymentMetrics.ChargeResult;
import com.bbcc.kidly.boundedcontext.payment.app.PaymentMetrics.SchedulerTask;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetryRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxRepository;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

@ExtendWith(MockitoExtension.class)
class PaymentMetricsTest {

    @Mock
    private PaymentOutboxRepository outboxRepository;

    @Mock
    private ChargeRepository chargeRepository;

    @Mock
    private OrderCancellationRetryRepository retryRepository;

    private SimpleMeterRegistry registry;
    private PaymentMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new PaymentMetrics(registry, outboxRepository, chargeRepository, retryRepository);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("아웃박스 게이지는 읽을 때마다 미전송 건수와 가장 오래된 미전송 메시지의 나이를 준다")
    void outboxGauges() {
        given(outboxRepository.countUnsent()).willReturn(3L);
        given(outboxRepository.findOldestUnsentCreatedAt())
            .willReturn(Optional.of(LocalDateTime.now().minusSeconds(90)))
            .willReturn(Optional.empty());

        assertThat(registry.get("kidly.payment.outbox.unsent.messages").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("kidly.payment.outbox.oldest.unsent.age.seconds").gauge().value())
            .isCloseTo(90.0, within(2.0));
        assertThat(registry.get("kidly.payment.outbox.oldest.unsent.age.seconds").gauge().value()).isZero();
    }

    @Test
    @DisplayName("멈춘 충전 게이지는 상태별로 30분 넘게 바뀌지 않은 충전 수를 준다")
    void chargesStuck() {
        given(chargeRepository.countByStatusAndUpdatedAtBefore(eq(ChargeStatus.CONFIRMING), any()))
            .willReturn(2L);
        given(chargeRepository.countByStatusAndUpdatedAtBefore(eq(ChargeStatus.CANCELLING), any()))
            .willReturn(1L);

        assertThat(registry.get("kidly.payment.charges.stuck").tag("status", "confirming").gauge().value())
            .isEqualTo(2.0);
        assertThat(registry.get("kidly.payment.charges.stuck").tag("status", "cancelling").gauge().value())
            .isEqualTo(1.0);
    }

    @Test
    @DisplayName("환불 재처리 게이지는 대기 건수와 가장 오래된 건의 나이를 준다")
    void refundRetry() {
        given(retryRepository.count()).willReturn(4L);
        given(retryRepository.findOldestCreatedAt()).willReturn(Optional.of(LocalDateTime.now().minusMinutes(10)));

        assertThat(registry.get("kidly.payment.refund.retry.pending").gauge().value()).isEqualTo(4.0);
        assertThat(registry.get("kidly.payment.refund.retry.oldest.age.seconds").gauge().value())
            .isCloseTo(600.0, within(2.0));
    }

    @Test
    @DisplayName("스케줄러 성공 시각은 처음 성공하기 전에는 NaN이고, 성공하면 그 시각(epoch 초)이다")
    void schedulerLastSuccess() {
        var gauge = registry.get("kidly.payment.scheduler.last.success.seconds").tag("task", "outbox_relay").gauge();
        assertThat(gauge.value()).isNaN();

        metrics.markSchedulerSuccess(SchedulerTask.OUTBOX_RELAY);

        assertThat(gauge.value()).isCloseTo(System.currentTimeMillis() / 1000.0, within(2.0));
        assertThat(registry.get("kidly.payment.scheduler.last.success.seconds").tag("task", "charge_expire")
            .gauge().value()).isNaN();
    }

    @Test
    @DisplayName("트랜잭션 밖이면 업무 결과를 바로 센다")
    void countOutsideTransaction() {
        metrics.countOrderPaid();
        metrics.countOrderPaymentFailed(PaymentFailureCode.INSUFFICIENT_BALANCE);
        metrics.countCharge(ChargeResult.UNKNOWN);
        metrics.countPayoutCompleted();

        assertThat(registry.get("kidly.payment.order.payments").tags("result", "success", "reason", "none")
            .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("kidly.payment.order.payments")
            .tags("result", "failure", "reason", "INSUFFICIENT_BALANCE").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("kidly.payment.charges").tag("result", "unknown").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("kidly.payment.payouts").tag("result", "completed").counter().count())
            .isEqualTo(1.0);
    }

    @Test
    @DisplayName("트랜잭션 안이면 커밋된 뒤에만 센다")
    void countAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        metrics.countOrderPaid();

        assertThat(registry.find("kidly.payment.order.payments").counter()).isNull();

        TransactionSynchronizationUtils.invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations());
        assertThat(registry.get("kidly.payment.order.payments").tags("result", "success").counter().count())
            .isEqualTo(1.0);
    }

    @Test
    @DisplayName("롤백되면 세지 않는다")
    void notCountedOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        metrics.countPayoutCompleted();

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationUtils.invokeAfterCompletion(synchronizations,
            TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(registry.find("kidly.payment.payouts").counter()).isNull();
    }

    @Test
    @DisplayName("아웃박스 전송 실패와 DLT는 eventType · 토픽별로 바로 센다")
    void countFailures() {
        TransactionSynchronizationManager.initSynchronization();
        metrics.countOutboxSendFailure("payment.completed");
        metrics.countDeadLetter("kidly.order.events");
        metrics.countDeadLetter("kidly.order.events");

        assertThat(registry.get("kidly.payment.outbox.send.failures").tag("event_type", "payment.completed")
            .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("kidly.payment.dlt.messages").tag("topic", "kidly.order.events")
            .counter().count()).isEqualTo(2.0);
    }
}
