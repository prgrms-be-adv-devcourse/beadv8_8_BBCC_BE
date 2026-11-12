package com.bbcc.kidly.boundedcontext.notification;

import static com.bbcc.kidly.boundedcontext.notification.NotificationFixture.EMAIL;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationSendService;
import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationPolicy;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.in.scheduler.NotificationRetryScheduler;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class NotificationRetrySchedulerIntegrationTest {

    @Autowired
    private NotificationRetryScheduler notificationRetryScheduler;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FakeNotificationEmailSender fakeNotificationEmailSender;

    @Autowired
    private NotificationSendService notificationSendService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    // 서버가 강제 종료(kill -9)되면 넘기기 전(PENDING) · 발송 중(SENDING)인 알림이 그 상태로 DB에 남는다.
    // 타임아웃이 지난 것만 다시 맡아 한 번씩 보내고, 아직 다른 서버가 보내는 중인 것은 건드리지 않는다
    @Test
    @DisplayName("서버가 발송 도중 죽어 남은 PENDING · SENDING은 타임아웃 뒤 한 번씩 보내고, 다음 주기에 다시 맡지 않는다")
    void recoverAfterCrash() {
        var pendingTo = "pending@kidly.co.kr";
        var sendingTo = "sending@kidly.co.kr";
        var inFlightTo = "in-flight@kidly.co.kr";
        var now = LocalDateTime.now();
        var pending = notificationRepository.save(NotificationFixture.welcomeEmail(pendingTo)).getId();
        jdbcTemplate.update("UPDATE notification.notification SET created_at = ? WHERE id = ?",
            now.minus(NotificationPolicy.PENDING_TIMEOUT).minusMinutes(1), pending);
        var sending = notificationRepository.save(NotificationFixture.sending(sendingTo,
            now.minus(NotificationPolicy.SENDING_TIMEOUT).minusMinutes(1))).getId();
        var inFlight = notificationRepository.save(NotificationFixture.sending(inFlightTo,
            now.minus(NotificationPolicy.SENDING_TIMEOUT).plusMinutes(1))).getId();

        notificationRetryScheduler.retry();
        AsyncWait.until(() -> statusOf(pending) == NotificationStatus.SENT
            && statusOf(sending) == NotificationStatus.SENT);

        // 다음 재시도 주기에 다시 맡을 알림이 없다(보낸 알림 · 아직 보내는 중인 알림 모두)
        assertThat(notificationSendService.findRetryTargets()).isEmpty();
        assertThat(statusOf(inFlight)).isEqualTo(NotificationStatus.SENDING);
        assertThat(fakeNotificationEmailSender.sent()).extracting(Email::to)
            .filteredOn(List.of(pendingTo, sendingTo, inFlightTo)::contains)
            .containsExactlyInAnyOrder(pendingTo, sendingTo);
    }

    private NotificationStatus statusOf(Long id) {
        return notificationRepository.findById(id).map(Notification::getStatus).orElseThrow();
    }
}
