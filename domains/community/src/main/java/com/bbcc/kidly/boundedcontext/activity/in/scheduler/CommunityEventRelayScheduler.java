package com.bbcc.kidly.boundedcontext.activity.in.scheduler;

import com.bbcc.kidly.boundedcontext.activity.app.CommunityEventMetrics;
import com.bbcc.kidly.boundedcontext.activity.app.CommunityEventRelayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티 이벤트 아웃박스를 주기마다 보낸다(kidly.kafka.producer.enabled가 true일 때, 기본 켜짐). 서버가 여러 대여도 한 대만 돈다
 * (DB 잠금). 커밋 뒤 Kafka로 보내는 빈이라 팀 공통 스위치를 따른다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
public class CommunityEventRelayScheduler {

    private final CommunityEventRelayService relayService;
    private final CommunityEventMetrics metrics;

    // 켜진 릴레이만 마지막 성공 시각 지표를 갖는다(처음 값은 기동 시각)
    public CommunityEventRelayScheduler(CommunityEventRelayService relayService, CommunityEventMetrics metrics) {
        this.relayService = relayService;
        this.metrics = metrics;
        metrics.registerRelay();
    }

    // fixedDelay는 이전 실행이 끝난 뒤부터 주기를 재므로 실행이 겹치지 않는다. 예외는 종류만 남긴다
    @Scheduled(fixedDelayString = "${kidly.community.events.relay.interval:PT1S}",
        initialDelayString = "${kidly.community.events.relay.interval:PT1S}")
    public void relay() {
        try {
            // 다른 서버가 잠금을 쥐어 건너뛴 주기는 성공으로 치지 않는다
            if (!relayService.relay().skipped()) {
                metrics.recordRelaySuccess();
            }
        } catch (RuntimeException e) {
            log.error("커뮤니티 이벤트 릴레이 주기가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
        }
    }
}
