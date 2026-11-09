package com.bbcc.kidly.boundedcontext.delivery.app;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 배송 컨텍스트 주기 작업(택배 조회)의 마지막 성공 시각(#1067). 주문 컨텍스트와 같은 지표 이름 · 태그 키(job)로 자기 작업 이름만 등록한다
 * (컨텍스트 사이 코드를 참조하지 않으므로 이름 · 설명을 같은 값으로 둔다).
 */
@Component
public class DeliveryJobMetrics {

    static final String SCHEDULER_LAST_SUCCESS = "kidly.order.scheduler.last.success.seconds";

    // 처음 성공하기 전에는 NaN(기동 직후 '오래 성공 못 함'으로 읽히지 않게). 인증값이 없어 택배 조회가 꺼져 있으면 계속 NaN이다
    private final AtomicLong trackingLastSuccess = new AtomicLong(-1);

    public DeliveryJobMetrics(MeterRegistry registry) {
        Gauge.builder(SCHEDULER_LAST_SUCCESS, trackingLastSuccess, value -> value.get() < 0 ? Double.NaN : value.get())
            .description("주문 서비스 주기 작업이 마지막으로 성공한 시각(epoch 초). 기동 뒤 아직 성공하지 않았으면 NaN")
            .tag("job", "delivery_tracking")
            .register(registry);
    }

    /**
     * 택배 조회 작업이 이 서버에서 끝까지 돌았다(건별 실패는 작업 실패가 아니다).
     */
    public void markTrackingSuccess() {
        trackingLastSuccess.set(System.currentTimeMillis() / 1000);
    }
}
