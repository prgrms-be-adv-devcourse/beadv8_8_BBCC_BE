package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.port.EmailSendException;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationEmailSender;
import com.bbcc.kidly.global.mail.Email;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 알림 한 건을 보낸다: 맡기(트랜잭션) → 발송(트랜잭션 밖) → 결과 기록(트랜잭션).
 * 다시 시도할 실패는 WARN, 더 시도하지 않는 실패만 ERROR로 남긴다(ERROR는 Slack 장애 채널로 간다).
 * 로그에는 주소를 남기지 않는다: 예외 객체 대신 알림 id와 고정된 실패 사유만 적는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationSendService notificationSendService;
    private final NotificationEmailSender notificationEmailSender;

    // 가입 같은 요청이 메일 발송을 기다리지 않도록 별도 스레드에서 보낸다
    @Async
    public void dispatchAsync(Long notificationId) {
        dispatch(notificationId);
    }

    public void dispatch(Long notificationId) {
        Optional<Email> email;
        try {
            email = notificationSendService.start(notificationId);
        } catch (OptimisticLockingFailureException e) {
            log.info("다른 곳이 먼저 맡은 알림이라 보내지 않습니다. notificationId={}", notificationId);
            return;
        }
        email.ifPresent(mail -> send(notificationId, mail));
    }

    private void send(Long notificationId, Email email) {
        try {
            notificationEmailSender.send(email);
            notificationSendService.recordSent(notificationId);
        } catch (EmailSendException e) {
            boolean willRetry = notificationSendService.recordFailed(notificationId, e.getMessage(), e.isRetryable());
            if (willRetry) {
                log.warn("이메일을 보내지 못해 다시 시도합니다. notificationId={} reason={}", notificationId, e.getMessage());
            } else {
                log.error("이메일을 보내지 못했고 더 시도하지 않습니다. notificationId={} reason={}", notificationId, e.getMessage());
            }
        }
    }
}
