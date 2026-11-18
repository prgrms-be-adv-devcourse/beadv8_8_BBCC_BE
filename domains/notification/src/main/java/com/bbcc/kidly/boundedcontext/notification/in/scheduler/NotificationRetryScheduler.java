package com.bbcc.kidly.boundedcontext.notification.in.scheduler;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationDispatcher;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 발송은 비동기로 넘겨 스케줄러 스레드를 붙잡지 않는다.
 * 발송 대기열이 가득 차면 남은 알림은 그대로 두고 다음 주기에 다시 집는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationSendService notificationSendService;
    private final NotificationDispatcher notificationDispatcher;

    @Scheduled(fixedDelayString = "${kidly.notification.retry-interval:PT1M}")
    public void retry() {
        try {
            notificationSendService.findRetryTargets().forEach(notificationDispatcher::dispatchAsync);
        } catch (TaskRejectedException e) {
            log.warn("발송 대기열이 가득 차 남은 재시도를 다음 주기로 미룹니다");
        }
    }
}
