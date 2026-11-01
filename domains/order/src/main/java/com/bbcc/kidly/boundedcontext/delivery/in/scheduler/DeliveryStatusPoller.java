package com.bbcc.kidly.boundedcontext.delivery.in.scheduler;

import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryTrackingProperties;
import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryTrackingService;
import com.bbcc.kidly.boundedcontext.delivery.app.dto.TrackingTarget;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryJobRunner;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 발송 중인 배송을 택배사에 조회해 완료된 건을 배송 완료한다. 주기는 kidly.order.delivery-tracking.interval(기본값은 order.yml).
 * 한 번에 한 서버에서만 돈다(DeliveryJobRunner, 잠금 키 kidly.delivery.delivery-tracking). 인증값이 없으면(비활성) 아무것도 하지 않는다.
 * 배송 ID 커서로 batch-size건씩 이어 읽고 max-per-run에 닿으면 끝난다. 한 건이 실패해도 WARN만 남기고 다음 건을 처리하며,
 * 실패 건은 다음 실행에서 다시 조회한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryStatusPoller {

    static final String JOB = "delivery-tracking";

    private final DeliveryTrackingService trackingService;
    // 주기 값의 바인딩 검증(1초 이상)이 @Scheduled 등록보다 먼저 돌게 하려고 주입한다(주문 OrderScheduler와 같음)
    private final DeliveryTrackingProperties properties;
    private final DeliveryJobRunner jobRunner;

    @Scheduled(fixedDelayString = "${kidly.order.delivery-tracking.interval}")
    public void trackDeliveries() {
        if (!trackingService.isEnabled()) {
            log.debug("스케줄러 건너뜀: job={} reason=disabled", JOB);
            return;
        }
        jobRunner.run(JOB, this::trackTargets);
    }

    private void trackTargets() {
        long afterId = 0L;
        int targets = 0;
        int failed = 0;
        while (targets < properties.maxPerRun()) {
            int limit = Math.min(properties.batchSize(), properties.maxPerRun() - targets);
            List<TrackingTarget> batch = trackingService.findTargets(afterId, limit);
            for (TrackingTarget target : batch) {
                try {
                    trackingService.track(target);
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("택배 조회 처리 실패: deliveryId={} exception={}", target.deliveryId(),
                        e.getClass().getSimpleName());
                }
            }
            targets += batch.size();
            if (batch.size() < limit) {
                break;
            }
            afterId = batch.getLast().deliveryId();
        }
        if (targets == 0) {
            log.debug("스케줄러 실행: job={} targets=0 interval={}", JOB, properties.interval());
            return;
        }
        log.info("스케줄러 실행: job={} targets={} processed={} failed={} interval={}",
            JOB, targets, targets - failed, failed, properties.interval());
    }
}
