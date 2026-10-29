package com.bbcc.kidly.boundedcontext.product.in.outbox;

import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 상품 아웃박스 릴레이와 정리를 주기로 부른다. 서비스 하나씩만 부르고 업무 로직은 두지 않는다.
 * 앱의 스케줄러 스레드는 다른 도메인 스케줄러와 함께 쓰는 하나뿐이다.
 * 브로커가 멈추면 릴레이 한 번이 최대 약 17초 걸려 그만큼 다른 스케줄러가 늦어질 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductOutboxScheduler {

    private final ProductOutboxRelayService relayService;
    private final ProductOutboxCleanupService cleanupService;

    // fixedDelay는 이전 실행이 끝난 뒤부터 주기를 재므로 릴레이가 겹치지 않는다.
    // DB 오류 메시지에 행 값(본문)이 들어갈 수 있어 예외 종류만 남긴다
    @Scheduled(fixedDelayString = "${kidly.product.outbox.relay-interval}")
    public void relay() {
        try {
            relayService.relay();
        } catch (RuntimeException e) {
            log.warn("상품 아웃박스 릴레이가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "${kidly.product.outbox.cleanup-cron}", zone = "Asia/Seoul")
    public void cleanUp() {
        cleanupService.cleanUp();
    }
}
