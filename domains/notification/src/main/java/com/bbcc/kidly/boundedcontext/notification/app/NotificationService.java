package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.EmailNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationChannel;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 도메인 이벤트로 알림을 만든다. 같은 이벤트 · 같은 채널은 한 건만 만들고, 커밋된 뒤에 비동기 발송을 넘긴다.
 * 커밋 전에 넘기면 다른 스레드가 아직 저장되지 않은 알림을 찾지 못한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final NotificationProperties notificationProperties;

    @Transactional
    public void createWelcomeEmail(WelcomeEmailCommand command) {
        if (notificationRepository.existsByEventIdAndChannel(command.eventId(), NotificationChannel.EMAIL)) {
            return;
        }
        var content = WelcomeEmail.compose(notificationProperties.siteUrl());
        var notification = notificationRepository.save(Notification.email(new EmailNotificationInfo(
            command.eventId(), command.memberId(), NotificationType.WELCOME, command.email(),
            content.title(), content.body())));
        dispatchAfterCommit(notification.getId());
    }

    private void dispatchAfterCommit(Long notificationId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationDispatcher.dispatchAsync(notificationId);
            }
        });
    }
}
