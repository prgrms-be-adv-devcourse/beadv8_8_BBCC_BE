package com.bbcc.kidly.boundedcontext.notification;

import static com.bbcc.kidly.boundedcontext.notification.NotificationFixture.EMAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationChannel;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NotificationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 12, 0);

    @Test
    @DisplayName("이메일 알림은 PENDING · 시도 0번으로 만든다")
    void createEmail() {
        var notification = NotificationFixture.welcomeEmail(EMAIL);

        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttemptCount()).isZero();
        assertThat(notification.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("PENDING이면 발송을 맡고 SENDING이 된다")
    void startSendingPending() {
        var notification = NotificationFixture.welcomeEmail(EMAIL);

        assertThat(notification.startSending(NOW)).isTrue();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENDING);
        assertThat(notification.getClaimedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("발송을 맡은 지 5분이 안 됐으면 다시 맡지 않는다")
    void startSendingRecentlyClaimed() {
        var notification = NotificationFixture.sending(EMAIL, NOW);

        assertThat(notification.startSending(NOW.plusMinutes(4))).isFalse();
        assertThat(notification.getClaimedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("발송을 맡은 채로 5분이 지나면 멈춘 것으로 보고 다시 맡는다")
    void startSendingStaleClaim() {
        var notification = NotificationFixture.sending(EMAIL, NOW);

        assertThat(notification.startSending(NOW.plusMinutes(6))).isTrue();
        assertThat(notification.getClaimedAt()).isEqualTo(NOW.plusMinutes(6));
    }

    @Test
    @DisplayName("FAILED는 다음 시도 시각이 되어야 다시 맡는다")
    void startSendingFailed() {
        var notification = NotificationFixture.retryableFailed(EMAIL, NOW);

        assertThat(notification.startSending(NOW.plusSeconds(29))).isFalse();
        assertThat(notification.startSending(NOW.plusSeconds(30))).isTrue();
        assertThat(notification.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("SENT · SKIPPED · 더 시도하지 않는 FAILED는 맡지 않는다")
    void startSendingFinished() {
        var sent = NotificationFixture.sending(EMAIL, NOW);
        sent.markSent(NOW);
        var skipped = NotificationFixture.sending(EMAIL, NOW);
        skipped.markSkipped();
        var gaveUp = NotificationFixture.sending(EMAIL, NOW);
        gaveUp.markFailed("인증 실패", false, NOW);

        assertThat(sent.startSending(NOW.plusDays(1))).isFalse();
        assertThat(skipped.startSending(NOW.plusDays(1))).isFalse();
        assertThat(gaveUp.startSending(NOW.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("보내면 SENT · 보낸 시각 · 시도 1번을 남긴다")
    void markSent() {
        var notification = NotificationFixture.sending(EMAIL, NOW);

        notification.markSent(NOW.plusSeconds(1));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(notification.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도할 수 있는 실패는 30초 → 2분 → 10분 뒤로 잡고, 4번째 실패에서 멈춘다")
    void retryBackoff() {
        var notification = NotificationFixture.sending(EMAIL, NOW);

        notification.markFailed("연결 실패", true, NOW);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(notification.willRetry()).isTrue();

        notification.startSending(NOW.plusSeconds(30));
        notification.markFailed("연결 실패", true, NOW);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(2));

        notification.startSending(NOW.plusMinutes(2));
        notification.markFailed("연결 실패", true, NOW);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(10));

        notification.startSending(NOW.plusMinutes(10));
        notification.markFailed("연결 실패", true, NOW);
        assertThat(notification.getAttemptCount()).isEqualTo(Notification.MAX_ATTEMPTS);
        assertThat(notification.getNextAttemptAt()).isNull();
        assertThat(notification.willRetry()).isFalse();
    }

    @Test
    @DisplayName("재시도할 수 없는 실패는 다음 시도 시각을 두지 않는다")
    void permanentFailure() {
        var notification = NotificationFixture.sending(EMAIL, NOW);

        notification.markFailed("인증 실패", false, NOW);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getNextAttemptAt()).isNull();
        assertThat(notification.willRetry()).isFalse();
    }

    @Test
    @DisplayName("실패 사유는 500자까지만 남긴다")
    void truncateError() {
        var notification = NotificationFixture.sending(EMAIL, NOW);

        notification.markFailed("e".repeat(600), true, NOW);

        assertThat(notification.getLastError()).hasSize(500);
    }

    @Test
    @DisplayName("발송을 맡지 않은 알림에는 결과를 기록할 수 없다")
    void recordWithoutSending() {
        var notification = NotificationFixture.welcomeEmail(EMAIL);

        assertThatThrownBy(() -> notification.markSent(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(notification::markSkipped).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> notification.markFailed("x", true, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"parent@kidly.test", "a@sub.example", "a@kidly.invalid", "a@my.localhost",
        "member@example.com", "a@EXAMPLE.ORG", "A@KIDLY.TEST", "a@localhost", "a@kidly.test.", "no-at-sign",
        " parent@kidly.test "})
    @DisplayName("주소가 없거나 예약 도메인이면 받을 수 없는 주소다")
    void undeliverableRecipient(String recipient) {
        assertThat(NotificationFixture.welcomeEmail(recipient).hasDeliverableRecipient()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"parent@kidly.co.kr", "someone@gmail.com", "a@testing.com"})
    @DisplayName("일반 도메인은 받을 수 있는 주소다")
    void deliverableRecipient(String recipient) {
        assertThat(NotificationFixture.welcomeEmail(recipient).hasDeliverableRecipient()).isTrue();
    }
}
