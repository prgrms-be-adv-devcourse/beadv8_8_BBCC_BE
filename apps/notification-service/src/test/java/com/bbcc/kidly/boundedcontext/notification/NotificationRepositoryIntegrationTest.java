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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class NotificationRepositoryIntegrationTest {

    private static final PageRequest FIRST_100 = PageRequest.of(0, 100);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
    void findRetryTargetIds() {
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
        assertThat(notificationRepository.findRetryTargetIds(
            now, now.plusMinutes(1), now.minusMinutes(5), FIRST_100))
            .containsExactly(dueFailed, staleSending, pending);
        assertThat(notificationRepository.findRetryTargetIds(
            now, now.minusMinutes(1), now.minusMinutes(5), FIRST_100))
            .containsExactly(dueFailed, staleSending)
            .doesNotContain(notDueFailed, recentSending);
    }

    @Test
    @DisplayName("재시도 대상은 정한 개수까지만 가져온다")
    void findRetryTargetIdsLimit() {
        var now = LocalDateTime.now();
        notificationRepository.save(NotificationFixture.retryableFailed(EMAIL, now.minusMinutes(1)));
        notificationRepository.save(NotificationFixture.retryableFailed(EMAIL, now.minusMinutes(1)));

        assertThat(notificationRepository.findRetryTargetIds(
            now, now.minusMinutes(1), now.minusMinutes(5), PageRequest.of(0, 1))).hasSize(1);
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
}
