package com.bbcc.kidly.boundedcontext.wish.app;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 조르기 컨텍스트 주기 작업(조르기 만료)의 마지막 성공 시각(#1067). 주문 컨텍스트와 같은 지표 이름 · 태그 키(job)로 자기 작업 이름만
 * 등록한다(조르기는 주문 코드를 참조하지 않으므로 이름 · 설명을 같은 값으로 둔다). 조르기가 꺼져 있으면 없다.
 */
@Component
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishJobMetrics {

    static final String SCHEDULER_LAST_SUCCESS = "kidly.order.scheduler.last.success.seconds";

    // 처음 성공하기 전에는 NaN. 만료가 꺼져 있으면(작업 빈 없음) 계속 NaN이다
    private final AtomicLong expiryLastSuccess = new AtomicLong(-1);

    public WishJobMetrics(MeterRegistry registry) {
        Gauge.builder(SCHEDULER_LAST_SUCCESS, expiryLastSuccess, value -> value.get() < 0 ? Double.NaN : value.get())
            .description("주문 서비스 주기 작업이 마지막으로 성공한 시각(epoch 초). 기동 뒤 아직 성공하지 않았으면 NaN")
            .tag("job", "wish_expiry")
            .register(registry);
    }

    /**
     * 조르기 만료 작업이 이 서버에서 끝까지 돌았다(건별 실패는 작업 실패가 아니다).
     */
    public void markExpirySuccess() {
        expiryLastSuccess.set(System.currentTimeMillis() / 1000);
    }
}
