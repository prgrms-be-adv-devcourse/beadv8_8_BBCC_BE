package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import com.bbcc.kidly.boundedcontext.notification.app.port.EmailSendException;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationEmailSender;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationPushSender;
import com.bbcc.kidly.boundedcontext.notification.app.port.OutgoingMessage;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushMessage;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushSendException;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushSendResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 알림 한 건을 보낸다: 맡기(트랜잭션) → 채널 발송기로 발송(트랜잭션 밖) → 결과 기록(트랜잭션).
 * 다시 시도할 실패는 WARN, 더 시도하지 않는 실패만 ERROR로 남긴다(ERROR는 Slack 장애 채널로 간다).
 * 로그에는 주소 · 푸시 FID를 남기지 않는다: 예외 객체 대신 알림 id와 고정된 실패 사유만 적는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationSendService notificationSendService;
    private final NotificationEmailSender notificationEmailSender;
    private final NotificationPushSender notificationPushSender;

    // 가입 같은 요청이 메일 발송을 기다리지 않도록 별도 스레드에서 보낸다
    @Async
    public void dispatchAsync(Long notificationId) {
        dispatch(notificationId);
    }

    public void dispatch(Long notificationId) {
        Optional<OutgoingMessage> message;
        try {
            message = notificationSendService.start(notificationId);
        } catch (OptimisticLockingFailureException e) {
            log.info("다른 곳이 먼저 맡은 알림이라 보내지 않습니다. notificationId={}", notificationId);
            return;
        }
        message.ifPresent(outgoing -> {
            switch (outgoing) {
                case Email email -> sendEmail(notificationId, email);
                case PushMessage push -> sendPush(notificationId, push);
            }
        });
    }

    private void sendEmail(Long notificationId, Email email) {
        try {
            notificationEmailSender.send(email);
            notificationSendService.recordSent(notificationId);
        } catch (EmailSendException e) {
            boolean willRetry = notificationSendService.recordFailed(notificationId, e.getMessage(), e.isRetryable());
            logFailure("이메일을", notificationId, e.getMessage(), willRetry);
        }
    }

    private void sendPush(Long notificationId, PushMessage push) {
        PushSendResult result;
        try {
            result = notificationPushSender.send(push);
        } catch (PushSendException e) {
            boolean willRetry = notificationSendService.recordFailed(notificationId, e.getMessage(), e.isRetryable());
            logFailure("푸시를", notificationId, e.getMessage(), willRetry);
            return;
        }
        boolean willRetry = notificationSendService.recordPushResult(notificationId, result);
        switch (result.outcome()) {
            case SENT, SKIPPED -> {
                if (!result.fidsToDelete().isEmpty()) {
                    log.info("무효 푸시 FID {}개를 지웠습니다. notificationId={}", result.fidsToDelete().size(),
                        notificationId);
                }
            }
            case RETRYABLE_FAILED, FAILED -> logFailure("푸시를", notificationId, result.failureReason(), willRetry);
        }
    }

    private static void logFailure(String what, Long notificationId, String reason, boolean willRetry) {
        if (willRetry) {
            log.warn("{} 보내지 못해 다시 시도합니다. notificationId={} reason={}", what, notificationId, reason);
        } else {
            log.error("{} 보내지 못했고 더 시도하지 않습니다. notificationId={} reason={}", what, notificationId, reason);
        }
    }
}
