package com.bbcc.kidly.boundedcontext.notification;

import static com.bbcc.kidly.boundedcontext.notification.NotificationFixture.EMAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.notification.domain.EmailNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationChannel;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class NotificationRepositoryIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 이벤트 · 같은 채널의 알림이 있는지 확인하고, 두 번째 저장은 유니크 제약으로 막는다")
    void existsByEventIdAndChannel() {
        var saved = notificationRepository.save(NotificationFixture.welcomeEmail(EMAIL));

        assertThat(notificationRepository.existsByEventIdAndChannel(saved.getEventId(), NotificationChannel.EMAIL))
            .isTrue();
        var duplicate = Notification.email(
            new EmailNotificationInfo(saved.getEventId(), 1L, saved.getType(), EMAIL, "t", "b"));
        assertThatThrownBy(() -> notificationRepository.save(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다음 시도 시각이 된 FAILED, 오래 남은 PENDING · SENDING만 재시도 대상이다")
    void lockRetryTargets() {
        var now = LocalDateTime.now();
        var dueFailed = notificationRepository.save(
            NotificationFixture.retryableFailed(EMAIL, now.minusMinutes(1))).getId();
        var notDueFailed = notificationRepository.save(
            NotificationFixture.retryableFailed(EMAIL, now)).getId();
        var staleSending = notificationRepository.save(
            NotificationFixture.sending(EMAIL, now.minusMinutes(10))).getId();
        var recentSending = notificationRepository.save(NotificationFixture.sending(EMAIL, now)).getId();
        var pending = notificationRepository.save(NotificationFixture.welcomeEmail(EMAIL)).getId();

        // PENDING은 방금 만들었으므로 기준 시각을 미래로 주면 오래 남은 것으로 잡힌다
        var withPending = transactionTemplate.execute(status -> notificationRepository.lockRetryTargets(
            now, now.plusMinutes(1), now.minusMinutes(5), 100).stream().map(Notification::getId).toList());
        var withoutPending = transactionTemplate.execute(status -> notificationRepository.lockRetryTargets(
            now, now.minusMinutes(1), now.minusMinutes(5), 100).stream().map(Notification::getId).toList());
        assertThat(withPending).containsExactly(dueFailed, staleSending, pending);
        assertThat(withoutPending).containsExactly(dueFailed, staleSending)
            .doesNotContain(notDueFailed, recentSending);
    }

    @Test
    @DisplayName("재시도 대상은 정한 개수까지만 가져온다")
    void lockRetryTargetsLimit() {
        var now = LocalDateTime.now();
        notificationRepository.save(NotificationFixture.retryableFailed(EMAIL, now.minusMinutes(1)));
        notificationRepository.save(NotificationFixture.retryableFailed(EMAIL, now.minusMinutes(1)));

        var targets = transactionTemplate.execute(status -> notificationRepository.lockRetryTargets(
            now, now.minusMinutes(1), now.minusMinutes(5), 1));
        assertThat(targets).hasSize(1);
    }

    @Test
    @DisplayName("다른 트랜잭션이 잠근 재시도 대상은 건너뛴다")
    void skipLockedRetryTargets() throws Exception {
        var now = LocalDateTime.now();
        var first = notificationRepository.save(
            NotificationFixture.retryableFailed(EMAIL, now.minusMinutes(1))).getId();
        var second = notificationRepository.save(
            NotificationFixture.retryableFailed(EMAIL, now.minusMinutes(1))).getId();
        var third = notificationRepository.save(
            NotificationFixture.retryableFailed(EMAIL, now.minusMinutes(1))).getId();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var holder = executor.submit(() -> transactionTemplate.execute(status -> {
                var ids = notificationRepository.lockRetryTargets(now, now.minusMinutes(1), now.minusMinutes(5), 2)
                    .stream().map(Notification::getId).toList();
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("잠금 해제 대기 시간 초과");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return ids;
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var remaining = transactionTemplate.execute(status -> notificationRepository
                    .lockRetryTargets(now, now.minusMinutes(1), now.minusMinutes(5), 2)
                    .stream().map(Notification::getId).toList());
                assertThat(remaining).containsExactly(third);
            } finally {
                release.countDown();
            }
            assertThat(holder.get(10, TimeUnit.SECONDS)).containsExactly(first, second);
        }
    }

    @Test
    @DisplayName("같은 알림을 두 곳이 맡으면 늦게 저장한 쪽은 버전 충돌로 거절된다")
    void optimisticLock() {
        var id = notificationRepository.save(NotificationFixture.welcomeEmail(EMAIL)).getId();
        var stale = notificationRepository.findById(id).orElseThrow();

        transactionTemplate.executeWithoutResult(status ->
            notificationRepository.findById(id).orElseThrow().startSending(LocalDateTime.now()));
        stale.startSending(LocalDateTime.now());

        assertThatThrownBy(() -> notificationRepository.save(stale))
            .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("기준 시각보다 먼저 만든 끝난 발송 기록(SENT · SKIPPED · 더 시도하지 않는 FAILED)만 지운다")
    void deleteFinishedCreatedBefore() {
        var cutoff = LocalDateTime.of(2025, 11, 9, 4, 5);
        var now = LocalDateTime.now();
        var oldSent = save(sent(now), cutoff.minusSeconds(1));
        var oldSkipped = save(skipped(now), cutoff.minusDays(10));
        var oldGivenUp = save(givenUp(now), cutoff.minusDays(10));
        var oldRetryable = save(NotificationFixture.retryableFailed(EMAIL, now), cutoff.minusDays(10));
        var oldPending = save(NotificationFixture.welcomeEmail(EMAIL), cutoff.minusDays(10));
        var oldSending = save(NotificationFixture.sending(EMAIL, now), cutoff.minusDays(10));
        var sentAtCutoff = save(sent(now), cutoff);

        var deleted = transactionTemplate.execute(status ->
            notificationRepository.deleteFinishedCreatedBefore(cutoff));

        assertThat(deleted).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList("SELECT id FROM notification.notification ORDER BY id", Long.class))
            .containsExactly(oldRetryable, oldPending, oldSending, sentAtCutoff)
            .doesNotContain(oldSent, oldSkipped, oldGivenUp);
    }

    private Long save(Notification notification, LocalDateTime createdAt) {
        var id = notificationRepository.save(notification).getId();
        jdbcTemplate.update("UPDATE notification.notification SET created_at = ? WHERE id = ?", createdAt, id);
        return id;
    }

    private static Notification sent(LocalDateTime now) {
        var notification = NotificationFixture.sending(EMAIL, now);
        notification.markSent(now);
        return notification;
    }

    private static Notification skipped(LocalDateTime now) {
        var notification = NotificationFixture.sending(EMAIL, now);
        notification.markSkipped();
        return notification;
    }

    private static Notification givenUp(LocalDateTime now) {
        var notification = NotificationFixture.sending(EMAIL, now);
        notification.markFailed("주소 거부", false, now);
        return notification;
    }
}
