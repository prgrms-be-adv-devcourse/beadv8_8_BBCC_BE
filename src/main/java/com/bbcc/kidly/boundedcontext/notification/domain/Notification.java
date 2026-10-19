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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    // 만들어진 뒤 이만큼 PENDING이면 비동기로 넘기기 전에 서버가 멈춘 것으로 본다
    public static final Duration PENDING_TIMEOUT = Duration.ofMinutes(1);

    // 발송을 맡은 채로 이보다 오래 끝나지 않으면 서버가 발송 중에 멈춘 것으로 보고 다시 맡는다
    public static final Duration SENDING_TIMEOUT = Duration.ofMinutes(5);
    private static final List<Duration> RETRY_DELAYS =
        List.of(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10));
    // 첫 시도 + 재시도 3번
    public static final int MAX_ATTEMPTS = RETRY_DELAYS.size() + 1;
    private static final int BODY_MAX_LENGTH = 4000;
    private static final int ERROR_MAX_LENGTH = 500;
    // RFC 2606 · 6761의 예약 도메인. 실제로 받을 수 없어 보내지 않는다 (초기 데이터 kidly.test, 테스트 example.com)
    private static final List<String> RESERVED_TLDS = List.of("test", "example", "invalid", "localhost");
    private static final Set<String> RESERVED_DOMAINS = Set.of("example.com", "example.net", "example.org");

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

    /**
     * 실패를 기록한다. 재시도할 수 있고 시도 횟수가 남았으면 다음 시도 시각을 30초 → 2분 → 10분 뒤로 잡는다.
     */
    public void markFailed(String error, boolean retryable, LocalDateTime now) {
        requireSending();
        this.attemptCount++;
        this.status = NotificationStatus.FAILED;
        this.lastError = truncate(error);
        this.nextAttemptAt = retryable && attemptCount < MAX_ATTEMPTS
            ? now.plus(RETRY_DELAYS.get(attemptCount - 1))
            : null;
    }

    public boolean willRetry() {
        return status == NotificationStatus.FAILED && nextAttemptAt != null;
    }

    public boolean hasDeliverableRecipient() {
        if (recipient == null || recipient.isBlank()) {
            return false;
        }
        var address = recipient.strip();
        if (!address.contains("@")) {
            return false;
        }
        var rawDomain = address.substring(address.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        var domain = rawDomain.endsWith(".") ? rawDomain.substring(0, rawDomain.length() - 1) : rawDomain;
        return !RESERVED_DOMAINS.contains(domain)
            && RESERVED_TLDS.stream().noneMatch(tld -> domain.equals(tld) || domain.endsWith("." + tld));
    }

    private boolean canStartSending(LocalDateTime now) {
        return switch (status) {
            case PENDING -> true;
            case FAILED -> nextAttemptAt != null && !nextAttemptAt.isAfter(now);
            case SENDING -> claimedAt.isBefore(now.minus(SENDING_TIMEOUT));
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
