package com.bbcc.kidly.boundedcontext.settlement.in.outbox;

import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxProperties;
import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRelayResult;
import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRelayResult.Outcome;
import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRelayService;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정산 이벤트 아웃박스 릴레이: 안 보낸 메시지를 주기마다 Kafka로 보낸다. 주기는 kidly.settlement.outbox.relay-interval.
 * 실패는 업무와 상관없이 WARN만 남긴다. DB 오류 메시지에 행 값(본문)이 들어갈 수 있어 예외 종류만 남긴다.
 * 다만 5분 넘게 계속 실패하면(지급 요청이 결제에 가지 못하는 상태) ERROR를 5분에 한 번 남겨 Grafana 알림이 울리게 한다.
 * kidly.kafka.producer.enabled가 false면 만들지 않는다(Kafka 없이 도는 테스트). 값이 없으면 만든다. 아웃박스 저장은 그때도 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SettlementOutboxRelayScheduler {

    private final SettlementOutboxRelayService relayService;
    // 주기의 바인딩 검증(1초 이상)이 @Scheduled 등록보다 먼저 돌아 키 이름이 나오는 오류로 실패하게 하려고 주입한다
    private final SettlementOutboxProperties properties;
    private static final Duration ALERT_AFTER = Duration.ofMinutes(5);
    // 연속 실패가 시작된 시각과 마지막 ERROR 시각. 성공하면 비운다
    private Instant failingSince;
    private Instant lastAlertedAt;

    // fixedDelay는 이전 실행이 끝난 뒤부터 주기를 재므로 한 서버 안에서 릴레이가 겹치지 않는다(서버끼리는 잠금이 막는다).
    // 첫 실행도 한 주기 뒤로 미뤄, 기동 직후 바로 돌지 않게 한다(테스트는 주기를 길게 둬서 돌지 않는다).
    // 보냈거나 멈췄으면 INFO, 보낼 것이 없거나 다른 릴레이가 도는 중이면 DEBUG
    @Scheduled(fixedDelayString = "${kidly.settlement.outbox.relay-interval}",
        initialDelayString = "${kidly.settlement.outbox.relay-interval}")
    public void relay() {
        relay(Instant.now());
    }

    // 테스트가 시각을 넣어 부른다(테스트는 다른 패키지에 있다)
    public void relay(Instant now) {
        SettlementOutboxRelayResult result;
        try {
            result = relayService.relay();
        } catch (RuntimeException e) {
            log.warn("정산 아웃박스 릴레이가 예외로 끝났습니다. error={}", e.getClass().getSimpleName());
            alertIfFailingLong(now);
            return;
        }
        if (result.outcome() == Outcome.STOPPED) {
            alertIfFailingLong(now);
        } else {
            failingSince = null;
            lastAlertedAt = null;
        }
        if (result.targets() == 0) {
            log.debug("정산 아웃박스 릴레이: outcome={} targets=0 interval={}", result.outcome(),
                properties.relayInterval());
            return;
        }
        log.info("정산 아웃박스 릴레이: outcome={} targets={} sent={}", result.outcome(), result.targets(), result.sent());
    }

    private void alertIfFailingLong(Instant now) {
        if (failingSince == null) {
            failingSince = now;
            return;
        }
        if (now.isBefore(failingSince.plus(ALERT_AFTER))
            || (lastAlertedAt != null && now.isBefore(lastAlertedAt.plus(ALERT_AFTER)))) {
            return;
        }
        log.error("정산 아웃박스를 {}분 넘게 보내지 못하고 있습니다. 지급 요청이 결제에 전달되지 않습니다. Kafka를 확인하세요",
            ALERT_AFTER.toMinutes());
        lastAlertedAt = now;
    }
}
