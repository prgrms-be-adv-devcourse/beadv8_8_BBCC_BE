package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
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

    /**
     * 발송을 맡는다. 맡았고 받을 수 있는 주소면 보낼 이메일을 돌려준다.
     * 받을 수 없는 주소면 SKIPPED로 바꾸고 비어 있는 값을 돌려준다.
     * 다른 곳이 같은 알림을 먼저 맡았으면 커밋할 때 버전 충돌 예외가 난다.
     */
    @Transactional
    public Optional<Email> start(Long notificationId) {
        Optional<Notification> found = notificationRepository.findById(notificationId);
        if (found.isEmpty() || !found.get().startSending(LocalDateTime.now())) {
            return Optional.empty();
        }
        var notification = found.get();
        if (!notification.hasDeliverableRecipient()) {
            notification.markSkipped();
            log.info("받을 수 없는 주소라 보내지 않습니다. notificationId={}", notificationId);
            return Optional.empty();
        }
        return Optional.of(new Email(notification.getRecipient(), notification.getTitle(),
            notification.getBody()));
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

    @Transactional(readOnly = true)
    public List<Long> findRetryTargets() {
        var now = LocalDateTime.now();
        return notificationRepository.findRetryTargetIds(now, now.minus(Notification.PENDING_TIMEOUT),
            now.minus(Notification.SENDING_TIMEOUT), PageRequest.of(0, RETRY_BATCH_SIZE));
    }
}
