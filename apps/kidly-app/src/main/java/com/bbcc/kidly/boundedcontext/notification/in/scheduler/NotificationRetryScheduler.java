package com.bbcc.kidly.boundedcontext.notification.in.scheduler;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationDispatcher;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationSendService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 다음 시도 시각이 된 실패 알림과 멈춘 알림(PENDING · SENDING)을 다시 보낸다.
 * 주기는 설정(kidly.notification.retry-interval)으로 정한다. 발송은 비동기로 넘겨 스케줄러 스레드를 붙잡지 않는다.
 * 스케줄러 스레드는 OrderScheduler 등과 함께 쓰는 하나뿐이다.
 */
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationSendService notificationSendService;
    private final NotificationDispatcher notificationDispatcher;

    @Scheduled(fixedDelayString = "${kidly.notification.retry-interval:PT1M}")
    public void retry() {
        notificationSendService.findRetryTargets().forEach(notificationDispatcher::dispatchAsync);
    }
}
