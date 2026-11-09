package com.bbcc.kidly.boundedcontext.order.app;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 주문 컨텍스트 주기 작업의 마지막 성공 시각 · 아웃박스 전송 실패 지표(#1067). 지표 이름 · 태그 값은 이 클래스에만 둔다.
 * 태그에는 작업 이름 · 이벤트 타입 · 결과 같은 고정 값만 넣는다(주문 · 회원 · 이벤트 ID 금지).
 * 같은 작업 시각 지표(kidly.order.scheduler.last.success.seconds)를 배송 · 조르기 컨텍스트도 자기 작업 이름으로 등록한다.
 */
@Component
public class OrderOperationMetrics {

    static final String SCHEDULER_LAST_SUCCESS = "kidly.order.scheduler.last.success.seconds";
    static final String OUTBOX_SEND_FAILURES = "kidly.order.outbox.send.failures";

    /**
     * 주문 컨텍스트의 주기 작업.
     */
    public enum Job { PAYMENT_EXPIRY, AUTO_CONFIRM, OUTBOX_RELAY, OUTBOX_CLEANUP, STUCK_DIAGNOSIS }

    /**
     * 전송 실패 결과: 다음 실행에서 다시 시도 · 영구 실패로 격리.
     */
    public enum SendFailure { RETRY, QUARANTINED }

    private final MeterRegistry registry;
    // 처음 성공하기 전에는 NaN이다. 기동 직후 '오래 성공 못 함'으로 잘못 읽히지 않게 한다(결제 #981과 같음)
    private final Map<Job, AtomicLong> lastSuccessEpochSeconds = new EnumMap<>(Job.class);

    public OrderOperationMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (Job job : Job.values()) {
            AtomicLong lastSuccess = new AtomicLong(-1);
            lastSuccessEpochSeconds.put(job, lastSuccess);
            Gauge.builder(SCHEDULER_LAST_SUCCESS, lastSuccess, value -> value.get() < 0 ? Double.NaN : value.get())
                .description("주문 서비스 주기 작업이 마지막으로 성공한 시각(epoch 초). 기동 뒤 아직 성공하지 않았으면 NaN")
                .tag("job", tagValue(job))
                .register(registry);
        }
    }

    /**
     * 그 작업이 이 서버에서 끝까지 돌았다(다른 서버가 돌아 건너뛴 경우 · 예외로 끝난 경우에는 부르지 않는다).
     */
    public void markJobSuccess(Job job) {
        lastSuccessEpochSeconds.get(job).set(System.currentTimeMillis() / 1000);
    }

    /**
     * 릴레이가 보내지 못한 일은 릴레이 트랜잭션과 상관없이 일어났으므로 바로 센다.
     *
     * @param eventType 이벤트 타입(정해진 값)
     */
    public void countOutboxSendFailure(String eventType, SendFailure result) {
        Counter.builder(OUTBOX_SEND_FAILURES)
            .description("주문 아웃박스 릴레이의 Kafka 전송 실패 횟수. outcome은 retry(다음 실행에서 다시) · quarantined(격리)")
            .tag("event_type", eventType)
            .tag("outcome", tagValue(result))
            .register(registry)
            .increment();
    }

    static String tagValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
