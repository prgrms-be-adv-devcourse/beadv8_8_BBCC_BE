package com.bbcc.kidly.boundedcontext.delivery.app;

import com.bbcc.kidly.boundedcontext.delivery.app.dto.DeliveryTrackingView;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryOrders;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingHistory;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingResult;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 구매자 택배 진행 이력(#1320, GET /api/v1/orders/{orderId}/deliveries/{sellerOrderId}/tracking, sellerOrderId = 배송 ID).
 * 저장하지 않고 볼 때 택배 조회에 묻는다.
 * <ul>
 *   <li>없는 배송 · 다른 주문의 배송 · 남의 주문은 404(TRACKING_DELIVERY_NOT_FOUND)</li>
 *   <li>발송 전(운송장 없음)은 200 · events [] · carrier · trackingNumber null, 조회할 수 없는 택배사(DUMMY)는 200 · events []
 *       (화면이 빈 이력으로 그린다). 운송장 형식 오류는 409, 조회 꺼짐 · 외부 실패 · 서킷 열림은 503(TRACKING_UNAVAILABLE)</li>
 *   <li>같은 배송 · 같은 운송장의 조회 결과만 10분(택배사에 아직 등록 전이면 1분) 캐시한다(외부 호출 한도 절약, 새 의존성 없이
 *       메모리). 실패는 캐시하지 않는다</li>
 * </ul>
 * 트랜잭션을 열지 않는다: 배송 · 주문은 각 조회가 자기 트랜잭션으로 읽고, 외부 호출 동안 DB 연결을 잡지 않는다.
 */
@Slf4j
@Service
public class TrackingHistoryService {

    static final Duration CACHE_TTL = Duration.ofMinutes(10);
    static final Duration NOT_REGISTERED_TTL = Duration.ofMinutes(1);
    // 요청마다 반복되는 이유(서킷 열림 · 꺼짐 · 호출 간격 자리 없음)는 DEBUG로만 남긴다
    private static final Set<String> QUIET_REASONS = Set.of(TrackingResult.CIRCUIT_OPEN, "DISABLED", "CALL_SLOT_BUSY");
    static final int CACHE_MAX = 1_000;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryOrders deliveryOrders;
    private final DeliveryStatusChecker checker;
    private final Clock clock;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public TrackingHistoryService(DeliveryRepository deliveryRepository, DeliveryOrders deliveryOrders,
        DeliveryStatusChecker checker) {
        this(deliveryRepository, deliveryOrders, checker, Clock.systemDefaultZone());
    }

    TrackingHistoryService(DeliveryRepository deliveryRepository, DeliveryOrders deliveryOrders,
        DeliveryStatusChecker checker, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryOrders = deliveryOrders;
        this.checker = checker;
        this.clock = clock;
    }

    public DeliveryTrackingView getTracking(Long memberId, Long orderId, Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .filter(found -> found.getOrderId().equals(orderId))
            .filter(found -> deliveryOrders.findOrder(orderId)
                .map(order -> Objects.equals(order.memberId(), memberId))
                .orElse(false))
            .orElseThrow(() -> new BusinessException(DeliveryErrorCode.TRACKING_DELIVERY_NOT_FOUND));
        if (delivery.getTrackingNumber() == null || delivery.getCarrier() == null) {
            return new DeliveryTrackingView(deliveryId, null, null, List.of());
        }
        Instant now = clock.instant();
        Cached cached = cache.get(deliveryId);
        if (cached != null && cached.matches(delivery) && now.isBefore(cached.expiresAt())) {
            return cached.view();
        }
        TrackingHistory history = checker.history(delivery.getCarrier(), delivery.getTrackingNumber());
        return switch (history.kind()) {
            // 조회할 수 없는 택배사(DUMMY)는 오류가 아니라 빈 이력이다. 외부를 부르지 않았으니 캐시하지 않는다
            case UNSUPPORTED_CARRIER -> new DeliveryTrackingView(deliveryId, delivery.getCarrier().name(),
                delivery.getTrackingNumber(), List.of());
            case OK -> {
                DeliveryTrackingView view = new DeliveryTrackingView(deliveryId, delivery.getCarrier().name(),
                    delivery.getTrackingNumber(), history.events());
                // 택배사에 아직 등록 전이면 곧 등록되므로 짧게만 캐시한다
                Duration ttl = history.isNotRegistered() ? NOT_REGISTERED_TTL : CACHE_TTL;
                remember(deliveryId, new Cached(delivery.getCarrier(), delivery.getTrackingNumber(), view,
                    now.plus(ttl)), now);
                yield view;
            }
            case INVALID_TRACKING_NUMBER -> throw new BusinessException(DeliveryErrorCode.TRACKING_INVALID_NUMBER);
            case UNAVAILABLE -> {
                // 운송장 · 응답 본문은 남기지 않는다(이유 코드만). 서킷 열림 · 꺼짐 · 자리 없음은 요청마다 반복되므로 DEBUG
                if (QUIET_REASONS.contains(history.reason())) {
                    log.debug("택배 진행 이력 조회 안 함: deliveryId={} reason={}", deliveryId, history.reason());
                } else {
                    log.warn("택배 진행 이력 조회 실패: deliveryId={} reason={}", deliveryId, history.reason());
                }
                throw new BusinessException(DeliveryErrorCode.TRACKING_UNAVAILABLE);
            }
        };
    }

    // 크기 상한: 넘으면 만료된 것부터 지우고, 그래도 넘으면 비운다(10분짜리 캐시라 다시 채워진다)
    private void remember(Long deliveryId, Cached cached, Instant now) {
        if (cache.size() >= CACHE_MAX) {
            cache.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
            if (cache.size() >= CACHE_MAX) {
                cache.clear();
            }
        }
        cache.put(deliveryId, cached);
    }

    int cacheSize() {
        return cache.size();
    }

    // 운송장이 바뀌었으면(판매자 정정 등) 캐시를 쓰지 않는다
    private record Cached(Delivery.Carrier carrier, String trackingNumber, DeliveryTrackingView view,
        Instant expiresAt) {

        boolean matches(Delivery delivery) {
            return carrier == delivery.getCarrier() && trackingNumber.equals(delivery.getTrackingNumber());
        }
    }
}
