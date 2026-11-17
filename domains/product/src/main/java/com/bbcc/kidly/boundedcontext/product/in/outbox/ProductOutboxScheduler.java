package com.bbcc.kidly.boundedcontext.product.in.outbox;

import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ProductOutboxScheduler {

    private final ProductOutboxRelayService relayService;
    private final ProductOutboxCleanupService cleanupService;

    @Scheduled(fixedDelayString = "${kidly.product.outbox.relay-interval}",
        initialDelayString = "${kidly.product.outbox.relay-interval}")
    public void relay() {
        try {
            relayService.relay();
        } catch (RuntimeException e) {
            log.error("상품 아웃박스 릴레이가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "${kidly.product.outbox.cleanup-cron}", zone = "Asia/Seoul")
    public void cleanUp() {
        try {
            cleanupService.cleanUp();
        } catch (RuntimeException e) {
            log.error("상품 아웃박스 정리가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }
}
