package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.payment.app.OrderPaymentService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PayOrderCommand;
import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetry;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetryRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 지표(#786)가 실제 DB 조회 · 커밋으로 값을 만드는지 본다.
 */
@IntegrationTest
class PaymentMetricsIntegrationTest {

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentOutboxRepository outboxRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ChargeRepository chargeRepository;

    @Autowired
    private OrderCancellationRetryRepository retryRepository;

    @Autowired
    private OrderPaymentService orderPaymentService;

    @Test
    @DisplayName("아웃박스 게이지는 보내지 않은 메시지만 세고, 가장 오래된 미전송 메시지의 나이를 준다")
    void outboxGauges() {
        LocalDateTime now = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(status -> {
            outboxRepository.saveIfAbsent(UUID.randomUUID(), "payment.completed", "kidly.payment.events", "1", "{}",
                now.minusMinutes(5));
            outboxRepository.saveIfAbsent(UUID.randomUUID(), "payment.completed", "kidly.payment.events", "2", "{}",
                now.minusMinutes(1));
            outboxRepository.saveIfAbsent(UUID.randomUUID(), "payment.completed", "kidly.payment.events", "3", "{}",
                now.minusHours(1));
        });
        jdbcTemplate.update("update payment.payment_outbox set sent_at = ? where message_key = '3'", now);

        assertThat(registry.get("kidly.payment.outbox.unsent.messages").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("kidly.payment.outbox.oldest.unsent.age.seconds").gauge().value())
            .isCloseTo(300.0, within(5.0));
    }

    @Test
    @DisplayName("멈춘 충전 게이지는 30분 넘게 승인 중 · 취소 중인 충전만 상태별로 센다")
    void chargesStuck() {
        Long walletId = walletRepository.save(PaymentFixture.wallet(0L)).getId();
        LocalDateTime now = LocalDateTime.now();
        Long stuckConfirming = saveCharge(walletId, "CONFIRMING", now.minusMinutes(31));
        saveCharge(walletId, "CONFIRMING", now.minusMinutes(10));
        saveCharge(walletId, "CANCELLING", now.minusHours(2));
        saveCharge(walletId, "DONE", now.minusHours(2));

        assertThat(stuckGauge("confirming")).isEqualTo(1.0);
        assertThat(stuckGauge("cancelling")).isEqualTo(1.0);

        jdbcTemplate.update("update payment.charge set status = 'DONE' where id = ?", stuckConfirming);
        assertThat(stuckGauge("confirming")).isZero();
    }

    @Test
    @DisplayName("환불 재처리 게이지는 대기 건수와 처음 실패한 기록의 나이를 준다")
    void refundRetryGauges() {
        assertThat(registry.get("kidly.payment.refund.retry.pending").gauge().value()).isZero();
        assertThat(registry.get("kidly.payment.refund.retry.oldest.age.seconds").gauge().value()).isZero();

        LocalDateTime now = LocalDateTime.now();
        retryRepository.save(OrderCancellationRetry.failed(1L, 10L, "EXPIRED", "boom", now));
        retryRepository.save(OrderCancellationRetry.failed(2L, 10L, "EXPIRED", "boom", now));
        jdbcTemplate.update("update payment.order_cancellation_retry set created_at = ? where order_id = 1",
            now.minusMinutes(20));

        assertThat(registry.get("kidly.payment.refund.retry.pending").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("kidly.payment.refund.retry.oldest.age.seconds").gauge().value())
            .isCloseTo(1200.0, within(5.0));
    }

    @Test
    @DisplayName("주문 결제 결과는 커밋된 뒤 결과 · 사유별로 센다")
    void orderPaymentCounter() {
        double successBefore = orderPayments("success", "none");
        double insufficientBefore = orderPayments("failure", "INSUFFICIENT_BALANCE");
        walletRepository.save(PaymentFixture.wallet(10_000L));

        orderPaymentService.pay(command(1L, 8_000L));
        orderPaymentService.pay(command(2L, 8_000L));

        assertThat(orderPayments("success", "none")).isEqualTo(successBefore + 1);
        assertThat(orderPayments("failure", "INSUFFICIENT_BALANCE")).isEqualTo(insufficientBefore + 1);
    }

    private PayOrderCommand command(Long orderId, long amount) {
        return new PayOrderCommand(orderId, PaymentFixture.MEMBER_ID, amount,
            List.of(new PayOrderCommand.Item(orderId * 100, amount)));
    }

    private Long saveCharge(Long walletId, String status, LocalDateTime updatedAt) {
        Long id = chargeRepository.save(
            Charge.request(walletId, UUID.randomUUID().toString(), 10_000L, LocalDateTime.now().plusMinutes(30)))
            .getId();
        jdbcTemplate.update("update payment.charge set status = ?, updated_at = ? where id = ?", status, updatedAt, id);
        return id;
    }

    private double stuckGauge(String status) {
        return registry.get("kidly.payment.charges.stuck").tag("status", status).gauge().value();
    }

    private double orderPayments(String result, String reason) {
        var counter = registry.find("kidly.payment.order.payments").tags("result", result, "reason", reason).counter();
        return counter == null ? 0 : counter.count();
    }
}
