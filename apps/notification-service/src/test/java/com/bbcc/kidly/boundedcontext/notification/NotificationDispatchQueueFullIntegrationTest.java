package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.in.scheduler.NotificationRetryScheduler;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@IntegrationTest
class NotificationDispatchQueueFullIntegrationTest {

    private static final int REJECTED_COUNT = 5;

    @Autowired
    private ThreadPoolTaskExecutor applicationTaskExecutor;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRetryScheduler notificationRetryScheduler;

    @Autowired
    private FakeNotificationEmailSender fakeNotificationEmailSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void tearDown() {
        // 실패해도 실행기를 풀어 같은 컨텍스트를 쓰는 다음 테스트를 막지 않는다
        release.countDown();
        fakeNotificationEmailSender.reset();
    }

    @Test
    @DisplayName("대기열이 가득 차 거절된 알림은 PENDING으로 남고, 재시도 스케줄러가 한 번씩만 보낸다")
    void rejectedNotificationsAreSentOnceByRetry() {
        fillExecutor();
        assertThatThrownBy(() -> applicationTaskExecutor.execute(() -> { }))
            .isInstanceOf(TaskRejectedException.class);
        List<String> recipients = IntStream.range(0, REJECTED_COUNT)
            .mapToObj(i -> "parent" + i + "@kidly.co.kr")
            .toList();

        recipients.forEach(email ->
            notificationService.createWelcomeEmail(new WelcomeEmailCommand(UUID.randomUUID(), 1L, email)));
        assertThat(emailStatuses()).hasSize(REJECTED_COUNT).containsOnly(NotificationStatus.PENDING);

        jdbcTemplate.update("UPDATE notification.notification SET created_at = created_at - INTERVAL '10 minutes'");
        notificationRetryScheduler.retry();
        assertThat(emailStatuses()).containsOnly(NotificationStatus.PENDING);

        release.countDown();
        AsyncWait.until(this::executorIdle);
        notificationRetryScheduler.retry();
        notificationRetryScheduler.retry();

        AsyncWait.until(() -> emailStatuses().stream().allMatch(status -> status == NotificationStatus.SENT));
        AsyncWait.until(this::executorIdle);
        assertThat(fakeNotificationEmailSender.sent()).map(Email::to)
            .containsExactlyInAnyOrderElementsOf(recipients);
    }

    private void fillExecutor() {
        Runnable blocker = () -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        for (int i = 0; i < applicationTaskExecutor.getMaxPoolSize(); i++) {
            applicationTaskExecutor.execute(blocker);
        }
        // 앞 테스트가 남긴 유휴 스레드가 대기열에서 작업을 꺼내 가는 중이면 빈 자리가 다시 생기므로, 모두 막힐 때까지 기다린다
        AsyncWait.until(() -> applicationTaskExecutor.getActiveCount() == applicationTaskExecutor.getMaxPoolSize());
        var queue = applicationTaskExecutor.getThreadPoolExecutor().getQueue();
        while (queue.remainingCapacity() > 0) {
            applicationTaskExecutor.execute(blocker);
        }
    }

    private boolean executorIdle() {
        return applicationTaskExecutor.getActiveCount() == 0
            && applicationTaskExecutor.getThreadPoolExecutor().getQueue().isEmpty();
    }

    private List<NotificationStatus> emailStatuses() {
        return jdbcTemplate.queryForList("SELECT status FROM notification.notification WHERE channel = 'EMAIL'",
            String.class).stream().map(NotificationStatus::valueOf).toList();
    }
}
