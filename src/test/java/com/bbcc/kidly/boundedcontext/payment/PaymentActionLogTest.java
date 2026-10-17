package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.bbcc.kidly.boundedcontext.payment.app.PaymentAction;
import com.bbcc.kidly.boundedcontext.payment.app.PaymentActionLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PaymentActionLogTest {

    private final ActionLogCapture capture = ActionLogCapture.start();

    @AfterEach
    void tearDown() {
        capture.close();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션 밖에서는 바로 action 뒤에 필드를 key=value로 이어 남긴다")
    void recordNow() {
        PaymentActionLog.record(PaymentAction.ORDER_PAID, "memberId={} amount={}", 1L, 15_000L);

        assertThat(capture.messages()).containsExactly("action=ORDER_PAID memberId=1 amount=15000");
    }

    @Test
    @DisplayName("없는 값은 null로 남긴다")
    void recordNull() {
        PaymentActionLog.record(PaymentAction.ORDER_PAYMENT_FAILED, "paymentId={}", (Object) null);

        assertThat(capture.messages()).containsExactly("action=ORDER_PAYMENT_FAILED paymentId=null");
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋된 뒤에 남긴다")
    void recordAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        PaymentActionLog.record(PaymentAction.ORDER_PAID, "memberId={}", 1L);

        assertThat(capture.messages()).isEmpty();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(capture.messages()).containsExactly("action=ORDER_PAID memberId=1");
    }

    @Test
    @DisplayName("action.payment가 INFO를 끄면 커밋 뒤 실행도 등록하지 않는다")
    void skipWhenInfoDisabled() {
        Logger logger = (Logger) LoggerFactory.getLogger(PaymentActionLog.LOGGER_NAME);
        Level level = logger.getLevel();
        logger.setLevel(Level.WARN);
        TransactionSynchronizationManager.initSynchronization();
        try {
            PaymentActionLog.record(PaymentAction.ORDER_PAID, "memberId={}", 1L);

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        } finally {
            logger.setLevel(level);
        }
    }

    @Test
    @DisplayName("롤백되면 남기지 않는다")
    void skipOnRollback() {
        TransactionSynchronizationManager.initSynchronization();

        PaymentActionLog.record(PaymentAction.ORDER_PAID, "memberId={}", 1L);

        TransactionSynchronizationManager.getSynchronizations()
            .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(capture.messages()).isEmpty();
    }
}
