package com.bbcc.kidly.boundedcontext.notification.in.scheduler;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보존 기간이 지난 알림함 알림을 하루 한 번 지운다. 시각은 설정(kidly.notification.inbox-cleanup-cron)으로 정한다.
 * 서버가 여러 대면 모두 실행되지만 같은 DELETE라 결과가 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxNotificationCleanupScheduler {

    private final InboxNotificationService inboxNotificationService;

    @Scheduled(cron = "${kidly.notification.inbox-cleanup-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void deleteExpired() {
        log.info("오래된 알림함 알림 {}건을 지웠습니다", inboxNotificationService.deleteExpired());
    }
}
