package com.bbcc.kidly.boundedcontext.childprofile.in.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.app.outbox.ChildProfileOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.childprofile.app.outbox.ChildProfileOutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ChildProfileOutboxScheduler {

    private final ChildProfileOutboxRelayService relayService;
    private final ChildProfileOutboxCleanupService cleanupService;

    @Scheduled(fixedDelayString = "${kidly.childprofile.outbox.relay-interval}",
        initialDelayString = "${kidly.childprofile.outbox.relay-interval}")
    public void relay() {
        runLoggingErrorTypeOnly("릴레이", relayService::relay);
    }

    @Scheduled(cron = "${kidly.childprofile.outbox.cleanup-cron}", zone = "Asia/Seoul")
    public void cleanUp() {
        runLoggingErrorTypeOnly("정리", cleanupService::cleanUp);
    }

    // DB 오류 메시지에 행 값(본문)이 들어갈 수 있어 예외 종류만 남긴다
    private static void runLoggingErrorTypeOnly(String job, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.warn("자녀 프로필 아웃박스 {}가 예외로 끝났습니다. error={}", job, e.getClass().getSimpleName());
        }
    }
}
