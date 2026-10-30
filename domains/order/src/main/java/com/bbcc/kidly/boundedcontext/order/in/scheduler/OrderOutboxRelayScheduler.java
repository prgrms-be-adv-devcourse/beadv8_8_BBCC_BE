package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxProperties;
import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxRelayService;
import com.bbcc.kidly.boundedcontext.order.app.outbox.dto.OutboxRelayResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주문 이벤트 아웃박스 릴레이(#540): 안 보낸 메시지를 주기마다 Kafka로 보낸다. 주기는 kidly.order.outbox.relay-interval
 * (기본값은 order.yml). 실패는 업무와 상관없이 WARN만 남긴다. 예외 메시지에 행 값(본문)이 들어갈 수 있어 예외 종류만 남긴다.
 * kidly.kafka.producer.enabled가 false면 만들지 않는다(Kafka 없이 도는 테스트). 값이 없으면 만든다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OrderOutboxRelayScheduler {

    private final OrderOutboxRelayService relayService;
    // 주기의 바인딩 검증(1초 이상)이 @Scheduled 등록보다 먼저 돌아 키 이름이 나오는 오류로 실패하게 하려고 주입한다
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
}
