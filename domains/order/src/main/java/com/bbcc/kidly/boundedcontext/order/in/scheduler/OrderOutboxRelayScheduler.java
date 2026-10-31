package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxProperties;
import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxRelayService;
import com.bbcc.kidly.boundedcontext.order.app.outbox.dto.OutboxRelayResult;
import com.bbcc.kidly.boundedcontext.order.app.port.SingleServerJobRunner;
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

    // 잠금 키 kidly.order.outbox-relay: #540 릴레이 서비스가 쓰던 키 그대로(배포 중 옛 서버와도 서로 막음)
    static final String RELAY_JOB = "outbox-relay";

    private final OrderOutboxRelayService relayService;
    // 주기의 바인딩 검증(1초 이상)이 @Scheduled 등록보다 먼저 돌아 키 이름이 나오는 오류로 실패하게 하려고 주입한다
    private final OrderOutboxProperties properties;
    private final SingleServerJobRunner jobRunner;

    /**
     * 보냈거나 멈췄으면 INFO, 보낼 것이 없으면 DEBUG. 다른 서버가 돌고 있으면 건너뛴다(러너의 DEBUG 한 줄).
     */
    @Scheduled(fixedDelayString = "${kidly.order.outbox.relay-interval}")
    public void relay() {
        OutboxRelayResult[] result = new OutboxRelayResult[1];
        try {
            // 한 번에 한 서버에서만(#564). 다른 서버가 돌고 있으면 러너가 DEBUG 한 줄 남기고 건너뛴다
            if (!jobRunner.run(RELAY_JOB, () -> result[0] = relayService.relay())) {
                return;
            }
        } catch (RuntimeException e) {
            log.warn("아웃박스 릴레이 실패: error={}", e.getClass().getSimpleName());
            return;
        }
        if (result[0].targets() == 0) {
            log.debug("아웃박스 릴레이: outcome={} targets=0 interval={}", result[0].outcome(),
                properties.relayInterval());
            return;
        }
        log.info("아웃박스 릴레이: outcome={} targets={} sent={}", result[0].outcome(), result[0].targets(),
            result[0].sent());
    }
}
