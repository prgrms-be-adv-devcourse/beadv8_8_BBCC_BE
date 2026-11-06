package com.bbcc.kidly.boundedcontext.notification.in.scheduler;

import com.bbcc.kidly.boundedcontext.notification.app.PushTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 오래 다시 등록되지 않은 푸시 FID를 하루 한 번 지운다. 시각은 설정(kidly.notification.push-token-cleanup-cron)으로 정한다.
 * 서버가 여러 대면 모두 실행되지만 같은 DELETE라 결과가 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushTokenCleanupScheduler {

    private final PushTokenService pushTokenService;

    @Scheduled(cron = "${kidly.notification.push-token-cleanup-cron:0 10 4 * * *}", zone = "Asia/Seoul")
    public void deleteExpired() {
        log.info("오래된 푸시 FID {}개를 지웠습니다", pushTokenService.deleteExpired());
    }
}
