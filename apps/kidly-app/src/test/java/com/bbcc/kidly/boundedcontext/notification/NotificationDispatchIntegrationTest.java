package com.bbcc.kidly.boundedcontext.notification;

import static com.bbcc.kidly.boundedcontext.notification.NotificationFixture.EMAIL;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationDispatcher;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 디스패처를 같은 스레드에서 불러 맡기 · 발송 · 결과 기록을 확인한다. 비동기 경로는 WelcomeEmailIntegrationTest가 확인한다.
 */
@IntegrationTest
class NotificationDispatchIntegrationTest {

    @Autowired
    private NotificationDispatcher notificationDispatcher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FakeNotificationEmailSender fakeNotificationEmailSender;

    @AfterEach
    void tearDown() {
        fakeNotificationEmailSender.reset();
    }

    @Test
    @DisplayName("보내면 SENT가 되고, 다시 불러도 한 번만 보낸다")
    void sendOnce() {
        Long id = save(EMAIL);

        notificationDispatcher.dispatch(id);
        notificationDispatcher.dispatch(id);

        assertThat(find(id).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(fakeNotificationEmailSender.sent()).singleElement().satisfies(email -> {
            assertThat(email.to()).isEqualTo(EMAIL);
            assertThat(email.subject()).isEqualTo(NotificationFixture.TITLE);
            assertThat(email.text()).isEqualTo(NotificationFixture.BODY);
        });
    }

    @Test
    @DisplayName("주소가 없거나 예약 도메인이면 보내지 않고 SKIPPED로 남긴다")
    void skipUndeliverable() {
        Long noRecipient = save(null);
        Long reserved = save("parent@kidly.test");

        notificationDispatcher.dispatch(noRecipient);
        notificationDispatcher.dispatch(reserved);

        assertThat(find(noRecipient).getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(find(reserved).getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(fakeNotificationEmailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("다시 보낼 수 있는 실패는 FAILED와 다음 시도 시각을 남긴다")
    void retryableFailure() {
        Long id = save(FakeNotificationEmailSender.RETRYABLE_FAILURE);

        notificationDispatcher.dispatch(id);

        Notification notification = find(id);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
        assertThat(notification.getNextAttemptAt()).isNotNull();
        assertThat(notification.getLastError()).isEqualTo("SMTP 연결 실패");
    }

    @Test
    @DisplayName("다시 보내도 같은 실패면 다음 시도 시각을 두지 않는다")
    void permanentFailure() {
        Long id = save(FakeNotificationEmailSender.PERMANENT_FAILURE);

        notificationDispatcher.dispatch(id);

        Notification notification = find(id);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getNextAttemptAt()).isNull();
    }

    private Long save(String recipient) {
        return notificationRepository.save(NotificationFixture.welcomeEmail(recipient)).getId();
    }

    private Notification find(Long id) {
        return notificationRepository.findById(id).orElseThrow();
    }
}
