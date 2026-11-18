package com.bbcc.kidly.boundedcontext.auth.in.outbox;

import com.bbcc.kidly.boundedcontext.auth.app.outbox.AuthOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.auth.app.outbox.AuthOutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
public class AuthOutboxScheduler {
    private final AuthOutboxRelayService relayService;
    private final AuthOutboxCleanupService cleanupService;

    @Scheduled(fixedDelayString = "${kidly.auth.outbox.relay-interval:PT1S}",
        initialDelayString = "${kidly.auth.outbox.relay-interval:PT1S}")
    public void relay() {
        try {
            relayService.relay();
        } catch (Exception e) {
            log.warn("인증 아웃박스 릴레이 실패 reason={}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "${kidly.auth.outbox.cleanup-cron}", zone = "Asia/Seoul")
    public void cleanUp() {
        try {
            cleanupService.cleanUp();
        } catch (RuntimeException e) {
            log.warn("인증 아웃박스 정리가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }
}
