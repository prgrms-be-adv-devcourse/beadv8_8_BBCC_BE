package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.app.port.OutgoingMessage;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushMessage;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushSendResult;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationPolicy;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.domain.PushTokenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메서드마다 짧은 트랜잭션 하나이고, 실제 발송(외부 I/O)은 트랜잭션 밖에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSendService {

    private static final int RETRY_BATCH_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final PushTokenRepository pushTokenRepository;
    private final NotificationProperties notificationProperties;
    private final NotificationMetrics notificationMetrics;

    /**
     * 다른 곳이 같은 알림을 먼저 맡았으면 커밋할 때 버전 충돌 예외가 난다.
     */
    @Transactional
    public Optional<OutgoingMessage> start(Long notificationId) {
        Optional<Notification> found = notificationRepository.findById(notificationId);
        if (found.isEmpty() || !found.get().startSending(LocalDateTime.now())) {
            return Optional.empty();
        }
        return createMessage(found.get());
    }

    @Transactional
    public Optional<OutgoingMessage> startClaimed(Long notificationId) {
        return notificationRepository.findById(notificationId)
            .filter(notification -> notification.getStatus() == NotificationStatus.SENDING)
            .flatMap(this::createMessage);
    }

    private Optional<OutgoingMessage> createMessage(Notification notification) {
        var message = switch (notification.getChannel()) {
            case EMAIL -> email(notification);
            case PUSH -> push(notification);
        };
        if (message.isEmpty()) {
            notification.markSkipped();
            notificationMetrics.recordResult(notification);
            log.info("보낼 곳이 없어 보내지 않습니다. notificationId={} channel={}", notification.getId(),
                notification.getChannel());
        }
        return message;
    }

    @Transactional
    public void recordSent(Long notificationId) {
        notificationRepository.findById(notificationId)
            .ifPresent(notification -> {
                notification.markSent(LocalDateTime.now());
                notificationMetrics.recordResult(notification);
            });
    }

    @Transactional
    public boolean recordFailed(Long notificationId, String error, boolean retryable) {
        return notificationRepository.findById(notificationId)
            .map(notification -> {
                notification.markFailed(error, retryable, LocalDateTime.now());
                notificationMetrics.recordResult(notification);
                return notification.willRetry();
            })
            .orElse(false);
    }

    /**
     * 다시 시도할 때는 남은 FID를 다시 찾아 보낸다.
     */
    @Transactional
    public boolean recordPushResult(Long notificationId, PushSendResult result) {
        var invalidFids = result.fidsToDelete();
        if (!invalidFids.isEmpty()) {
            pushTokenRepository.deleteByFidIn(invalidFids);
        }
        return notificationRepository.findById(notificationId)
            .map(notification -> {
                var now = LocalDateTime.now();
                switch (result.outcome()) {
                    case SENT -> notification.markSent(now);
                    case SKIPPED -> notification.markSkipped();
                    case RETRYABLE_FAILED -> notification.markFailed(result.failureReason(), true, now);
                    case FAILED -> notification.markFailed(result.failureReason(), false, now);
                }
                notificationMetrics.recordResult(notification);
                return notification.willRetry();
            })
            .orElse(false);
    }

    /**
     * 다시 보낼 알림을 최대 {@code maxCount}건(한 번에 100건까지) 골라 SENDING으로 맡고 ID를 돌려준다.
     * 다른 Pod가 맡는 중인 행은 건너뛰므로 Pod마다 서로 다른 알림을 맡는다.
     */
    @Transactional
    public List<Long> claimRetryTargets(int maxCount) {
        if (maxCount <= 0) {
            return List.of();
        }
        var now = LocalDateTime.now();
        return notificationRepository.lockRetryTargets(now, now.minus(NotificationPolicy.PENDING_TIMEOUT),
                now.minus(NotificationPolicy.SENDING_TIMEOUT), Math.min(maxCount, RETRY_BATCH_SIZE)).stream()
            .filter(notification -> notification.startSending(now))
            .map(Notification::getId)
            .toList();
    }

    private static Optional<OutgoingMessage> email(Notification notification) {
        if (!notification.hasDeliverableRecipient()) {
            return Optional.empty();
        }
        return Optional.of(new Email(notification.getRecipient(), notification.getTitle(), notification.getBody()));
    }

    private Optional<OutgoingMessage> push(Notification notification) {
        var fids = pushTokenRepository.findFidsOf(notification.getMemberId());
        if (fids.isEmpty()) {
            return Optional.empty();
        }
        var path = notification.getLinkPath() == null ? "/" : notification.getLinkPath();
        return Optional.of(new PushMessage(fids, notification.getTitle(), notification.getBody(),
            notificationProperties.siteUrl() + path));
    }
}
