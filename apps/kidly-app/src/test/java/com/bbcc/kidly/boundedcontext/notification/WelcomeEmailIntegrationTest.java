package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationChannel;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.boundedcontext.notification.out.JpaNotificationRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * member.registered가 커밋되면 환영 메일을 저장하고 별도 스레드에서 보낸다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 * FakeNotificationEmailSender는 컨텍스트를 함께 쓰는 다른 테스트의 늦은 발송도 받으므로, 이 테스트의 받는 사람으로 걸러 본다.
 */
@IntegrationTest
class WelcomeEmailIntegrationTest {

    private static final String EMAIL = "parent@kidly.co.kr";

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JpaNotificationRepository jpaNotificationRepository;

    @Autowired
    private FakeNotificationEmailSender fakeNotificationEmailSender;

    @AfterEach
    void tearDown() {
        fakeNotificationEmailSender.reset();
    }

    @Test
    @DisplayName("가입 이벤트가 커밋되면 환영 메일을 저장하고 비동기로 보낸다")
    void sendWelcomeEmail() {
        publishInTransaction(memberRegistered(EMAIL));

        AsyncWait.until(() -> statusesOf(EMAIL).equals(List.of(NotificationStatus.SENT)));
        assertThat(notificationsOf(EMAIL)).singleElement().satisfies(notification -> {
            assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(notification.getType()).isEqualTo(NotificationType.WELCOME);
            assertThat(notification.getMemberId()).isEqualTo(1L);
            assertThat(notification.getRecipient()).isEqualTo(EMAIL);
            assertThat(notification.getAttemptCount()).isEqualTo(1);
        });
        assertThat(sentTo(EMAIL)).singleElement().satisfies(email -> {
            assertThat(email.subject()).isEqualTo("[Kidly] 가입을 환영합니다");
            assertThat(email.text()).contains("http://localhost:5173");
        });
    }

    @Test
    @DisplayName("같은 가입 이벤트를 두 번 받아도 메일은 한 통이다")
    void duplicateEvent() {
        var event = memberRegistered(EMAIL);

        publishInTransaction(event);
        publishInTransaction(event);

        AsyncWait.until(() -> statusesOf(EMAIL).equals(List.of(NotificationStatus.SENT)));
        assertThat(sentTo(EMAIL)).hasSize(1);
    }

    @Test
    @DisplayName("초기 데이터처럼 예약 도메인으로 가입하면 메일을 보내지 않는다")
    void skipReservedDomain() {
        var reserved = "parent@kidly.test";

        publishInTransaction(memberRegistered(reserved));

        AsyncWait.until(() -> statusesOf(reserved).equals(List.of(NotificationStatus.SKIPPED)));
        assertThat(sentTo(reserved)).isEmpty();
    }

    private List<Notification> notificationsOf(String recipient) {
        return jpaNotificationRepository.findAll().stream()
            .filter(notification -> recipient.equals(notification.getRecipient()))
            .toList();
    }

    private List<NotificationStatus> statusesOf(String recipient) {
        return notificationsOf(recipient).stream().map(Notification::getStatus).toList();
    }

    private List<Email> sentTo(String recipient) {
        return fakeNotificationEmailSender.sent().stream().filter(email -> email.to().equals(recipient)).toList();
    }

    private void publishInTransaction(MemberRegisteredEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private static MemberRegisteredEvent memberRegistered(String email) {
        return new MemberRegisteredEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberRegisteredEvent.Data(1L, email));
    }
}
