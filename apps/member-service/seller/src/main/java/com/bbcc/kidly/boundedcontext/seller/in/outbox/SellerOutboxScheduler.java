package com.bbcc.kidly.boundedcontext.seller.in.outbox;

import com.bbcc.kidly.boundedcontext.seller.app.outbox.SellerOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.seller.app.outbox.SellerOutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 앱의 스케줄러 스레드는 다른 도메인 스케줄러와 함께 쓰는 하나뿐이다.
 * 브로커가 멈추면 릴레이 한 번이 최대 약 15초 걸려 그만큼 다른 스케줄러가 늦어질 수 있다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SellerOutboxScheduler {

    private final SellerOutboxRelayService relayService;
    private final SellerOutboxCleanupService cleanupService;

    // fixedDelay는 이전 실행이 끝난 뒤부터 주기를 재므로 릴레이가 겹치지 않는다.
    // 첫 실행도 한 주기 뒤로 미뤄, 기동 직후 바로 돌지 않게 한다(테스트는 주기를 길게 둬서 돌지 않는다).
    // DB 오류 메시지에 행 값(본문)이 들어갈 수 있어 예외 종류만 남긴다.
    @Scheduled(fixedDelayString = "${kidly.seller.outbox.relay-interval}",
        initialDelayString = "${kidly.seller.outbox.relay-interval}")
    public void relay() {
        try {
            relayService.relay();
        } catch (RuntimeException e) {
            log.warn("판매자 아웃박스 릴레이가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }

    // 릴레이와 같은 이유로 예외 종류만 남긴다.
    @Scheduled(cron = "${kidly.seller.outbox.cleanup-cron}", zone = "Asia/Seoul")
    public void cleanUp() {
        try {
            cleanupService.cleanUp();
        } catch (RuntimeException e) {
            log.warn("판매자 아웃박스 정리가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }
}
