package com.bbcc.kidly.boundedcontext.notification;

import static com.bbcc.kidly.boundedcontext.notification.NotificationFixture.EMAIL;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationDispatcher;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.support.IntegrationTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 발송 결과 카운터와 미발송 게이지가 실제 발송 · DB 상태를 따라가는지 본다(#1071). 카운터는 누적이라 전후 차이로 본다.
 */
@IntegrationTest
class NotificationMetricsIntegrationTest {

    @Autowired
    private NotificationDispatcher notificationDispatcher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FakeNotificationEmailSender fakeNotificationEmailSender;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void tearDown() {
        fakeNotificationEmailSender.reset();
    }

    @Test
    @DisplayName("발송 결과를 채널 · 결과별로 세고, 보내지 못한 알림은 게이지에 남는다")
    void countsOutcomesAndUnsent() {
        double unsentBefore = gauge("kidly.notification.unsent");
        double sentBefore = sends("sent");
        double skippedBefore = sends("skipped");
        double retryBefore = sends("retry");
        double failedBefore = sends("failed");

        Long sent = save(EMAIL);
        Long skipped = save(null);
        Long retry = save(FakeNotificationEmailSender.RETRYABLE_FAILURE);
        Long failed = save(FakeNotificationEmailSender.PERMANENT_FAILURE);
        assertThat(gauge("kidly.notification.unsent")).isEqualTo(unsentBefore + 4);
        assertThat(gauge("kidly.notification.oldest.unsent.age.seconds")).isGreaterThanOrEqualTo(0);

        for (Long id : new Long[] {sent, skipped, retry, failed}) {
            notificationDispatcher.dispatch(id);
        }

        assertThat(sends("sent")).isEqualTo(sentBefore + 1);
        assertThat(sends("skipped")).isEqualTo(skippedBefore + 1);
        assertThat(sends("retry")).isEqualTo(retryBefore + 1);
        assertThat(sends("failed")).isEqualTo(failedBefore + 1);
        // 다시 시도할 실패만 아직 보내지 못한 것으로 남는다
        assertThat(gauge("kidly.notification.unsent")).isEqualTo(unsentBefore + 1);
    }

    private Long save(String recipient) {
        return notificationRepository.save(NotificationFixture.welcomeEmail(recipient)).getId();
    }

    private double gauge(String name) {
        return meterRegistry.get(name).gauge().value();
    }

    private double sends(String outcome) {
        Counter counter = meterRegistry.find("kidly.notification.sends").tags("channel", "email", "outcome", outcome)
            .counter();
        return counter == null ? 0 : counter.count();
    }
}
