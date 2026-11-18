package com.bbcc.kidly.boundedcontext.notification;

import static com.bbcc.kidly.boundedcontext.notification.NotificationFixture.EMAIL;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationDispatcher;
import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 롤링 배포로 Pod가 겹쳐도 Notification의 @Version 낙관적 잠금으로 한 곳만 보낸다. */
@IntegrationTest
class NotificationDispatchConcurrencyIntegrationTest {

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
    @DisplayName("배포 중 Pod 두 개가 같은 알림을 동시에 맡으려 해도 한 번만 보낸다")
    void dispatchOnceWhenPodsCompete() throws Exception {
        var id = notificationRepository.save(NotificationFixture.welcomeEmail(EMAIL)).getId();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                notificationDispatcher.dispatch(id);
                return null;
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                notificationDispatcher.dispatch(id);
                return null;
            });
            ready.await();
            start.countDown();
            first.get();
            second.get();
        }

        assertThat(fakeNotificationEmailSender.sent()).filteredOn((Email email) -> EMAIL.equals(email.to())).hasSize(1);
        assertThat(notificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(NotificationStatus.SENT);
    }
}
