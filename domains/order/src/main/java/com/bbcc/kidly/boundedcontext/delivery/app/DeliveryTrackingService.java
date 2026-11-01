package com.bbcc.kidly.boundedcontext.delivery.app;

import com.bbcc.kidly.boundedcontext.delivery.app.dto.TrackingTarget;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 발송 중인 배송 한 건을 택배사에 조회해 결과대로 처리한다. 외부 호출(최대 수 초)을 DB 트랜잭션 밖에 두려고
 * 클래스 단위 @Transactional인 DeliveryService와 나눴다. 상태 변경은 DeliveryService의 짧은 트랜잭션에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryTrackingService {

    private final DeliveryStatusChecker checker;
    private final DeliveryService deliveryService;

    public boolean isEnabled() {
        return checker.isEnabled();
    }

    public List<TrackingTarget> findTargets(Long afterId, int limit) {
        return deliveryService.findTrackingTargets(afterId, limit);
    }

    public void track(TrackingTarget target) {
        TrackingResult result = checker.check(target.carrier(), target.trackingNumber());
        switch (result.kind()) {
            case DELIVERED -> deliveryService.completeByTracking(target.deliveryId(), result.deliveredAt());
            case INVALID_TRACKING_NUMBER -> deliveryService.stopTracking(target.deliveryId(), result.reason());
            case IN_PROGRESS -> {
            }
            case UNAVAILABLE -> log.warn("택배 조회 실패: deliveryId={} reason={}", target.deliveryId(), result.reason());
        }
    }
}
