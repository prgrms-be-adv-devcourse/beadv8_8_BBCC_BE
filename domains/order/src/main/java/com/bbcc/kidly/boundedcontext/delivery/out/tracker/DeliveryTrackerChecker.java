package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingResult;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.delivery.out.config.DeliveryTrackerProperties;
import java.time.Duration;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import org.springframework.web.client.RestClient;

/**
 * DeliveryStatusChecker의 Delivery Tracker 구현. 택배사 enum → Delivery Tracker 택배사 ID 변환,
 * 응답 → TrackingResult 변환, 호출 간격 유지(호출 한도 초당 10회)를 맡는다. 외부 서비스 ID는 domain에 두지 않는다.
 */
public class DeliveryTrackerChecker implements DeliveryStatusChecker {

    // 10/15 실제 API carriers 목록으로 확인한 ID
    static final Map<Delivery.Carrier, String> CARRIER_IDS = new EnumMap<>(Map.of(
        Delivery.Carrier.CJ, "kr.cjlogistics",
        Delivery.Carrier.EPOST, "kr.epost",
        Delivery.Carrier.HANJIN, "kr.hanjin",
        Delivery.Carrier.LOTTE, "kr.lotte",
        Delivery.Carrier.LOGEN, "kr.logen",
        Delivery.Carrier.DUMMY, "dev.track.dummy"));

    private static final String DELIVERED = "DELIVERED";

    private final DeliveryTrackerClient client;
    private final long minCallIntervalNanos;
    private final ZoneId zone;
    private long lastCallNanos;

    DeliveryTrackerChecker(DeliveryTrackerClient client, Duration minCallInterval, ZoneId zone) {
        this.client = client;
        this.minCallIntervalNanos = minCallInterval.toNanos();
        this.zone = zone;
    }

    /**
     * 빈 설정(out/config의 DeliveryTrackerConfig, #651)이 쓴다. 클라이언트는 받은 빌더를 그대로 써서 테스트에서 가짜 서버를 붙일 수 있다.
     */
    public static DeliveryStatusChecker create(DeliveryTrackerProperties properties, RestClient.Builder builder,
        ZoneId zone) {
        return new DeliveryTrackerChecker(new DeliveryTrackerClient(properties, builder), properties.minCallInterval(),
            zone);
    }

    @Override
    public boolean isEnabled() {
        return client.isEnabled();
    }

    @Override
    public TrackingResult check(Delivery.Carrier carrier, String trackingNumber) {
        waitForCallInterval();
        try {
            TrackResult result = client.track(CARRIER_IDS.get(carrier), trackingNumber);
            if (!DELIVERED.equals(result.statusCode())) {
                return TrackingResult.inProgress();
            }
            // 택배사마다 오프셋이 달라(Z · +09:00) 같은 순간의 서버 시간대 시각으로 바꿔 shippedAt(LocalDateTime)과 비교한다
            return TrackingResult.delivered(result.eventTime().atZoneSameInstant(zone).toLocalDateTime());
        } catch (DeliveryTrackerException e) {
            return e.isInvalidTrackingNumber()
                ? TrackingResult.invalidTrackingNumber(e.code())
                : TrackingResult.unavailable(e.code());
        }
    }

    // 호출 한도(초당 10회)를 넘지 않게 호출 사이를 띄운다. 스케줄러 한 스레드가 부르지만 다른 호출이 생겨도 지켜지게 동기화한다
    private synchronized void waitForCallInterval() {
        long waitNanos = lastCallNanos + minCallIntervalNanos - System.nanoTime();
        if (lastCallNanos != 0 && waitNanos > 0) {
            LockSupport.parkNanos(waitNanos);
        }
        lastCallNanos = System.nanoTime();
    }
}
