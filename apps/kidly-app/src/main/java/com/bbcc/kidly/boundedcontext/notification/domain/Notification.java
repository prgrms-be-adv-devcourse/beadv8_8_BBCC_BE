package com.bbcc.kidly.boundedcontext.notification.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 보낼 알림 한 건. 채널 하나에 한 건이고, 같은 이벤트 · 같은 채널은 한 건만 만든다((event_id, channel) 유니크).
 * 제목과 본문은 만들 때 완성해 두어 재시도해도 같은 내용이 나간다.
 * 커밋 뒤 비동기 발송과 재시도 스케줄러가 같은 알림을 동시에 맡으려 하면 {@code @Version}으로 한쪽만 저장된다.
 */
@Entity
@Table(schema = "notification", name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    private static final int BODY_MAX_LENGTH = 4000;
    private static final int ERROR_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(length = 320)
    private String recipient;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = BODY_MAX_LENGTH)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attemptCount;

    private LocalDateTime nextAttemptAt;

    // 마지막으로 발송을 맡은 시각
    private LocalDateTime claimedAt;

    private LocalDateTime sentAt;

    @Column(length = ERROR_MAX_LENGTH)
    private String lastError;

    @Version
    private Long version;

    public static Notification email(EmailNotificationInfo info) {
        // 비어 있으면 발송할 때 메일을 만들지 못해 같은 알림을 계속 다시 맡게 된다
        Assert.hasText(info.title(), "이메일 알림의 제목은 비어 있을 수 없습니다");
        Assert.hasText(info.body(), "이메일 알림의 본문은 비어 있을 수 없습니다");
        var notification = new Notification();
        notification.eventId = info.eventId();
        notification.channel = NotificationChannel.EMAIL;
        notification.memberId = info.memberId();
        notification.type = info.type();
        notification.recipient = info.recipient();
        notification.title = info.title();
        notification.body = info.body();
        notification.status = NotificationStatus.PENDING;
        return notification;
    }

    /**
     * 발송을 맡는다. 맡을 수 있으면 SENDING으로 바꾸고 true를 돌려준다.
     * 맡을 수 있는 것: 처음 만든 알림, 다음 시도 시각이 된 실패 알림, 발송을 맡은 채로 멈춘 알림.
     */
    public boolean startSending(LocalDateTime now) {
        if (!canStartSending(now)) {
            return false;
        }
        this.status = NotificationStatus.SENDING;
        this.claimedAt = now;
        this.nextAttemptAt = null;
        return true;
    }

    public void markSent(LocalDateTime now) {
        requireSending();
        this.attemptCount++;
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.nextAttemptAt = null;
    }

    // 보낼 주소가 없거나 받을 수 없는 주소다. 시도로 세지 않는다
    public void markSkipped() {
        requireSending();
        this.status = NotificationStatus.SKIPPED;
    }

    // 실패를 기록한다. 다음 시도 시각은 NotificationPolicy가 정한다
    public void markFailed(String error, boolean retryable, LocalDateTime now) {
        requireSending();
        this.attemptCount++;
        this.status = NotificationStatus.FAILED;
        this.lastError = truncate(error);
        this.nextAttemptAt = NotificationPolicy.nextAttemptAt(attemptCount, retryable, now);
    }

    public boolean willRetry() {
        return status == NotificationStatus.FAILED && nextAttemptAt != null;
    }

    public boolean hasDeliverableRecipient() {
        return NotificationPolicy.isDeliverableEmail(recipient);
    }

    private boolean canStartSending(LocalDateTime now) {
        return switch (status) {
            case PENDING -> true;
            case FAILED -> nextAttemptAt != null && !nextAttemptAt.isAfter(now);
            case SENDING -> claimedAt.isBefore(now.minus(NotificationPolicy.SENDING_TIMEOUT));
            case SENT, SKIPPED -> false;
        };
    }

    private void requireSending() {
        if (status != NotificationStatus.SENDING) {
            throw new IllegalStateException("발송을 맡은 알림에만 결과를 기록할 수 있습니다: " + status);
        }
    }

    private static String truncate(String error) {
        return error == null || error.length() <= ERROR_MAX_LENGTH ? error : error.substring(0, ERROR_MAX_LENGTH);
    }
}
