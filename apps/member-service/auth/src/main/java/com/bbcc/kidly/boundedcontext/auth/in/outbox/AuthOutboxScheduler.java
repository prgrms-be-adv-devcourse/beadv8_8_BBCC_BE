package com.bbcc.kidly.boundedcontext.auth.in.outbox;

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

    @Scheduled(fixedDelayString = "${kidly.auth.outbox.relay-interval:PT1S}",
        initialDelayString = "${kidly.auth.outbox.relay-interval:PT1S}")
    public void relay() {
        try {
            relayService.relay();
        } catch (Exception e) {
            log.warn("인증 아웃박스 릴레이 실패 reason={}", e.getClass().getSimpleName());
        }
    }
}
