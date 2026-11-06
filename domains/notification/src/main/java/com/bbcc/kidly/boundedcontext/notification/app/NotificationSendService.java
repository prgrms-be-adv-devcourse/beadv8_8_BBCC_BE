package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.app.port.OutgoingMessage;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushMessage;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushSendResult;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationPolicy;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.PushTokenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송을 맡고 결과를 기록한다. 메서드마다 짧은 트랜잭션 하나이고, 실제 발송(외부 I/O)은 트랜잭션 밖에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSendService {

    private static final int RETRY_BATCH_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final PushTokenRepository pushTokenRepository;
    private final NotificationProperties notificationProperties;

    /**
     * 발송을 맡는다. 맡았고 보낼 곳이 있으면 채널에 맞는 보낼 내용을 돌려준다.
     * 보낼 곳이 없으면(받을 수 없는 이메일 주소, 푸시 FID 없음) SKIPPED로 바꾸고 비어 있는 값을 돌려준다.
     * 다른 곳이 같은 알림을 먼저 맡았으면 커밋할 때 버전 충돌 예외가 난다.
     */
    @Transactional
    public Optional<OutgoingMessage> start(Long notificationId) {
        Optional<Notification> found = notificationRepository.findById(notificationId);
        if (found.isEmpty() || !found.get().startSending(LocalDateTime.now())) {
            return Optional.empty();
        }
        var notification = found.get();
        var message = switch (notification.getChannel()) {
            case EMAIL -> email(notification);
            case PUSH -> push(notification);
        };
        if (message.isEmpty()) {
            notification.markSkipped();
            log.info("보낼 곳이 없어 보내지 않습니다. notificationId={} channel={}", notificationId,
                notification.getChannel());
        }
        return message;
    }

    @Transactional
    public void recordSent(Long notificationId) {
        notificationRepository.findById(notificationId)
            .ifPresent(notification -> notification.markSent(LocalDateTime.now()));
    }

    /**
     * 실패를 기록하고, 다시 시도할 예정이면 true를 돌려준다.
     */
    @Transactional
    public boolean recordFailed(Long notificationId, String error, boolean retryable) {
        return notificationRepository.findById(notificationId)
            .map(notification -> {
                notification.markFailed(error, retryable, LocalDateTime.now());
                return notification.willRetry();
            })
            .orElse(false);
    }

    /**
     * 푸시 결과를 기록한다. 무효 FID를 지우고, 결과(PushSendResult.outcome)대로 알림 상태를 바꾼다.
     * 다시 시도할 예정이면 true를 돌려준다. 다시 시도할 때는 남은 FID를 다시 찾아 보낸다.
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
                return notification.willRetry();
            })
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<Long> findRetryTargets() {
        var now = LocalDateTime.now();
        return notificationRepository.findRetryTargetIds(now, now.minus(NotificationPolicy.PENDING_TIMEOUT),
            now.minus(NotificationPolicy.SENDING_TIMEOUT), PageRequest.of(0, RETRY_BATCH_SIZE));
    }

    private static Optional<OutgoingMessage> email(Notification notification) {
        if (!notification.hasDeliverableRecipient()) {
            return Optional.empty();
        }
        return Optional.of(new Email(notification.getRecipient(), notification.getTitle(), notification.getBody()));
    }

    // 경로가 없는 푸시는 누르면 사이트 첫 화면을 연다
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
