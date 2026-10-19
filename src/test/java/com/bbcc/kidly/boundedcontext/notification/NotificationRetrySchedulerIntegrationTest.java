package com.bbcc.kidly.boundedcontext.notification;

import static com.bbcc.kidly.boundedcontext.notification.NotificationFixture.EMAIL;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.in.scheduler.NotificationRetryScheduler;
import com.bbcc.kidly.global.mail.Email;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class NotificationRetrySchedulerIntegrationTest {

    @Autowired
    private NotificationRetryScheduler notificationRetryScheduler;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FakeNotificationEmailSender fakeNotificationEmailSender;

    @AfterEach
    void tearDown() {
        fakeNotificationEmailSender.reset();
    }

    @Test
    @DisplayName("다음 시도 시각이 된 실패 알림을 다시 보내고, 더 시도하지 않는 실패는 건드리지 않는다")
    void retryDueFailures() {
        var anHourAgo = LocalDateTime.now().minusHours(1);
        var due = notificationRepository.save(NotificationFixture.retryableFailed(EMAIL, anHourAgo)).getId();
        var gaveUp = NotificationFixture.sending(EMAIL, anHourAgo);
        gaveUp.markFailed("SMTP 인증 실패", false, anHourAgo);
        var gaveUpId = notificationRepository.save(gaveUp).getId();

        notificationRetryScheduler.retry();

        AsyncWait.until(() -> statusOf(due) == NotificationStatus.SENT);
        assertThat(statusOf(gaveUpId)).isEqualTo(NotificationStatus.FAILED);
        assertThat(fakeNotificationEmailSender.sent()).filteredOn((Email email) -> EMAIL.equals(email.to())).hasSize(1);
    }

    @Test
    @DisplayName("발송을 맡은 채로 멈춘 알림을 다시 보낸다")
    void retryStaleSending() {
        var stale = notificationRepository.save(
            NotificationFixture.sending(EMAIL, LocalDateTime.now().minusMinutes(10))).getId();

        notificationRetryScheduler.retry();

        AsyncWait.until(() -> statusOf(stale) == NotificationStatus.SENT);
    }

    private NotificationStatus statusOf(Long id) {
        return notificationRepository.findById(id).map(Notification::getStatus).orElseThrow();
    }
}
