package com.bbcc.kidly.boundedcontext.notification.in.scheduler;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationDispatcher;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationSendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 발송은 비동기로 넘겨 스케줄러 스레드를 붙잡지 않는다. 발송 실행기에 남은 자리만큼만 맡는다:
 * 맡은 알림은 SENDING이라 넘기지 못하면 5분(SENDING_TIMEOUT) 뒤에야 다시 맡기 때문이다.
 * 맡은 뒤 다른 발송이 자리를 먼저 채워 거절되면 남은 알림은 그 5분 뒤에 보낸다.
 */
@Slf4j
@Component
public class NotificationRetryScheduler {

    private final NotificationSendService notificationSendService;
    private final NotificationDispatcher notificationDispatcher;
    private final ThreadPoolTaskExecutor sendExecutor;

    public NotificationRetryScheduler(NotificationSendService notificationSendService,
                                      NotificationDispatcher notificationDispatcher,
                                      @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor sendExecutor) {
        this.notificationSendService = notificationSendService;
        this.notificationDispatcher = notificationDispatcher;
        this.sendExecutor = sendExecutor;
    }

    @Scheduled(fixedDelayString = "${kidly.notification.retry-interval:PT1M}")
    public void retry() {
        try {
            notificationSendService.claimRetryTargets(freeSlots())
                .forEach(notificationDispatcher::dispatchClaimedAsync);
        } catch (TaskRejectedException e) {
            log.warn("발송 대기열이 가득 차 남은 재시도를 5분 뒤로 미룹니다");
        }
    }

    private int freeSlots() {
        var idleThreads = sendExecutor.getMaxPoolSize() - sendExecutor.getActiveCount();
        var freeQueue = sendExecutor.getQueueCapacity() - sendExecutor.getQueueSize();
        return Math.max(0, idleThreads) + Math.max(0, freeQueue);
    }
}
