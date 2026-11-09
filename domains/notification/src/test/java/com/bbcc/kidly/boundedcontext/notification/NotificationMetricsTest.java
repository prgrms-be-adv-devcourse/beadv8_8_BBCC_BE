package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationMetrics;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 발송 결과는 기록이 커밋된 뒤에만 센다. 버전 충돌로 롤백된 기록이 실패율을 흐리지 않게 한다(#1071).
 */
class NotificationMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NotificationMetrics metrics = new NotificationMetrics(registry, mock(NotificationRepository.class));

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("커밋되면 세고, 롤백되면 세지 않는다")
    void countsOnlyAfterCommit() {
        metrics.recordResult(sent());
        metrics.recordResult(sent());
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();

        assertThat(sentCount()).isZero();
        synchronizations.getFirst().afterCommit();
        synchronizations.getLast().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(sentCount()).isEqualTo(1);
    }

    private static Notification sent() {
        var notification = NotificationFixture.welcomeEmail(NotificationFixture.EMAIL);
        var now = LocalDateTime.now();
        notification.startSending(now);
        notification.markSent(now);
        return notification;
    }

    private double sentCount() {
        return registry.get("kidly.notification.sends").tags("channel", "email", "outcome", "sent").counter().count();
    }
}
