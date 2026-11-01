package com.bbcc.kidly.boundedcontext.delivery.in.scheduler;

import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryTrackingProperties;
import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryTrackingService;
import com.bbcc.kidly.boundedcontext.delivery.app.dto.TrackingTarget;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 발송 중인 배송을 택배사에 조회해 완료된 건을 배송 완료한다. 주기는 kidly.order.delivery-tracking.interval(기본값은 order.yml).
 * 한 번에 한 서버에서만 돈다(DeliveryJobRunner, 잠금 키 kidly.delivery.delivery-tracking). 인증값이 없으면(비활성) 아무것도 하지 않는다.
 * 배송 ID 커서로 batch-size건씩 이어 읽고 max-per-run에 닿으면 끝난다. 한 건이 실패해도 WARN만 남기고 다음 건을 처리하며,
 * 실패 건은 다음 실행에서 다시 조회한다. 배송 중 · 일시 오류인 배송은 다음 실행에도 대상에 남으므로 순환 커서({@link RotatingCursor},
 * #656)로 다음 실행이 멈춘 곳 다음부터 이어 읽는다(앞쪽이 상한 이상 남아도 뒤쪽 배송이 차례가 온다). 상한에 닿은 실행은 실행 로그 한 줄을
 * WARN으로 남긴다.
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
    private final RotatingCursor cursor = new RotatingCursor();

    @Scheduled(fixedDelayString = "${kidly.order.delivery-tracking.interval}")
    public void trackDeliveries() {
        if (!trackingService.isEnabled()) {
            log.debug("스케줄러 건너뜀: job={} reason=disabled", JOB);
            return;
        }
        jobRunner.run(JOB, this::trackTargets);
    }

    private void trackTargets() {
        RotatingCursor.Run run = cursor.run(properties.batchSize(), properties.maxPerRun(),
            trackingService::findTargets, TrackingTarget::deliveryId, target -> {
                try {
                    trackingService.track(target);
                    return true;
                } catch (RuntimeException e) {
                    log.warn("택배 조회 처리 실패: deliveryId={} exception={}", target.deliveryId(),
                        e.getClass().getSimpleName());
                    return false;
                }
            });
        int targets = run.targets();
        int failed = run.failed();
        if (targets == 0) {
            log.debug("스케줄러 실행: job={} targets=0 interval={}", JOB, properties.interval());
            return;
        }
        if (run.capped()) {
            log.warn("스케줄러 상한 도달: job={} targets={} processed={} failed={} maxPerRun={} resumeAfterId={} interval={}",
                JOB, targets, targets - failed, failed, properties.maxPerRun(), cursor.resumeAfterId(),
                properties.interval());
            return;
        }
        log.info("스케줄러 실행: job={} targets={} processed={} failed={} interval={}",
            JOB, targets, targets - failed, failed, properties.interval());
    }
}
