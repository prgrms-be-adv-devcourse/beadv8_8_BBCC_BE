package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxProperties;
import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxRelayService;
import com.bbcc.kidly.boundedcontext.order.app.outbox.dto.OutboxRelayResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주문 이벤트 아웃박스의 시간 작업(#540): 릴레이(안 보낸 메시지를 Kafka로) · 정리(보관 기간이 지난 보낸 메시지 삭제).
 * 주기는 설정(kidly.order.outbox.relay-interval · cleanup-cron, 기본값은 order.yml)으로 정한다.
 * 실패는 업무와 상관없이 WARN만 남긴다. 예외 메시지에 행 값(본문)이 들어갈 수 있어 예외 종류만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxScheduler {

    private final OrderOutboxRelayService relayService;
    // 주기 · 보관 기간의 바인딩 검증(1초 이상)이 @Scheduled 등록보다 먼저 돌아 키 이름이 나오는 오류로 실패하게 하려고 주입한다
    private final OrderOutboxProperties properties;

    /**
     * 보냈거나 멈췄으면 INFO, 보낼 것이 없거나 다른 릴레이가 도는 중이면 DEBUG.
     */
    @Scheduled(fixedDelayString = "${kidly.order.outbox.relay-interval}")
    public void relay() {
        OutboxRelayResult result;
        try {
            result = relayService.relay();
        } catch (RuntimeException e) {
            log.warn("아웃박스 릴레이 실패: error={}", e.getClass().getSimpleName());
            return;
        }
        if (result.targets() == 0) {
            log.debug("아웃박스 릴레이: outcome={} targets=0 interval={}", result.outcome(), properties.relayInterval());
            return;
        }
        log.info("아웃박스 릴레이: outcome={} targets={} sent={}", result.outcome(), result.targets(), result.sent());
    }

    @Scheduled(cron = "${kidly.order.outbox.cleanup-cron}")
    public void cleanUp() {
        try {
            int deleted = relayService.cleanUp();
            log.info("아웃박스 정리: deleted={} retention={}", deleted, properties.retention());
        } catch (RuntimeException e) {
            log.warn("아웃박스 정리 실패: error={}", e.getClass().getSimpleName());
        }
    }
}
