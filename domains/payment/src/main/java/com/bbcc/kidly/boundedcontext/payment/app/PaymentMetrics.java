package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetryRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxRepository;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 결제 업무 · 아웃박스 · DLT 지표(#786). 지표 이름 · 태그 값은 이 클래스에만 둔다. 태그에 회원 · 주문 ID 같은 값을 넣지 않는다.
 * 게이지는 수집할 때마다 부분 인덱스 · 작은 표만 읽는다. 업무 결과 카운터는 커밋된 뒤에만 센다(롤백된 일은 세지 않는다).
 */
@Component
public class PaymentMetrics {

    static final Duration CHARGE_STUCK_AFTER = Duration.ofMinutes(30);

    private static final String OUTBOX_UNSENT = "kidly.payment.outbox.unsent.messages";
    private static final String OUTBOX_OLDEST_UNSENT_AGE = "kidly.payment.outbox.oldest.unsent.age.seconds";
    private static final String OUTBOX_SEND_FAILURES = "kidly.payment.outbox.send.failures";
    private static final String DLT_MESSAGES = "kidly.payment.dlt.messages";
    private static final String ORDER_PAYMENTS = "kidly.payment.order.payments";
    private static final String CHARGES = "kidly.payment.charges";
    private static final String CHARGES_STUCK = "kidly.payment.charges.stuck";
    private static final String REFUND_RETRY_PENDING = "kidly.payment.refund.retry.pending";
    private static final String REFUND_RETRY_OLDEST_AGE = "kidly.payment.refund.retry.oldest.age.seconds";
    private static final String PAYOUTS = "kidly.payment.payouts";
    private static final String SCHEDULER_LAST_SUCCESS = "kidly.payment.scheduler.last.success.seconds";

    private static final String REASON_NONE = "none";

    public enum ChargeResult { DONE, REJECTED, UNKNOWN }

    public enum SchedulerTask {
        CHARGE_RECONCILE_CONFIRMING, CHARGE_RECONCILE_CANCELLING, CHARGE_EXPIRE, OUTBOX_RELAY,
        ORDER_CANCELLATION_RETRY
    }

    private final MeterRegistry registry;
    // 처음 성공하기 전에는 NaN이다. 기동 직후 '오래 성공 못 함'으로 잘못 읽히지 않게 한다
    private final Map<SchedulerTask, AtomicLong> lastSuccessEpochSeconds = new EnumMap<>(SchedulerTask.class);

    public PaymentMetrics(MeterRegistry registry, PaymentOutboxRepository outboxRepository,
        ChargeRepository chargeRepository, OrderCancellationRetryRepository retryRepository) {
        this.registry = registry;
        Gauge.builder(OUTBOX_UNSENT, outboxRepository::countUnsent)
            .description("아직 Kafka로 보내지 않은 결제 아웃박스 메시지 수")
            .register(registry);
        Gauge.builder(OUTBOX_OLDEST_UNSENT_AGE, () -> ageSeconds(outboxRepository::findOldestUnsentCreatedAt))
            .description("가장 오래된 미전송 결제 아웃박스 메시지의 나이(초). 없으면 0")
            .register(registry);
        for (ChargeStatus status : new ChargeStatus[] {ChargeStatus.CONFIRMING, ChargeStatus.CANCELLING}) {
            Gauge.builder(CHARGES_STUCK, () -> chargeRepository.countByStatusAndUpdatedAtBefore(status,
                    LocalDateTime.now().minus(CHARGE_STUCK_AFTER)))
                .description("30분 넘게 승인 중 · 취소 중인 충전 수")
                .tag("status", tagValue(status))
                .register(registry);
        }
        Gauge.builder(REFUND_RETRY_PENDING, retryRepository::count)
            .description("처리하지 못해 다시 처리를 기다리는 주문 취소 환불 수")
            .register(registry);
        Gauge.builder(REFUND_RETRY_OLDEST_AGE, () -> ageSeconds(retryRepository::findOldestCreatedAt))
            .description("가장 오래된 재처리 대기 주문 취소 환불의 나이(초). 없으면 0")
            .register(registry);
        for (SchedulerTask task : SchedulerTask.values()) {
            AtomicLong lastSuccess = new AtomicLong(-1);
            lastSuccessEpochSeconds.put(task, lastSuccess);
            Gauge.builder(SCHEDULER_LAST_SUCCESS, lastSuccess, v -> v.get() < 0 ? Double.NaN : v.get())
                .description("결제 스케줄러가 마지막으로 성공한 시각(epoch 초). 기동 뒤 아직 성공하지 않았으면 NaN")
                .tag("task", tagValue(task))
                .register(registry);
        }
    }

    public void countOrderPaid() {
        AfterCommit.run(() -> orderPayments("success", REASON_NONE).increment());
    }

    public void countOrderPaymentFailed(PaymentFailureCode reason) {
        AfterCommit.run(() -> orderPayments("failure", reason.name()).increment());
    }

    public void countCharge(ChargeResult result) {
        AfterCommit.run(() -> Counter.builder(CHARGES)
            .description("충전 승인 요청에 대한 토스 응답 결과")
            .tag("result", tagValue(result))
            .register(registry)
            .increment());
    }

    // 지금은 지급 실패가 생기지 않는다(판매자 지갑이 없으면 만들어 입금한다). 실패가 생기면 result 값을 더한다
    public void countPayoutCompleted() {
        AfterCommit.run(() -> Counter.builder(PAYOUTS)
            .description("정산 지급 입금 결과(처음 처리한 정산서만)")
            .tag("result", "completed")
            .register(registry)
            .increment());
    }

    /**
     * 릴레이가 보내지 못한 일은 릴레이 트랜잭션과 상관없이 일어났으므로 바로 센다.
     */
    public void countOutboxSendFailure(String eventType) {
        Counter.builder(OUTBOX_SEND_FAILURES)
            .description("결제 아웃박스 릴레이의 Kafka 전송 실패 횟수")
            .tag("event_type", eventType)
            .register(registry)
            .increment();
    }

    public void countDeadLetter(String topic) {
        Counter.builder(DLT_MESSAGES)
            .description("처리하지 못해 DLT로 보낸 결제 수신 메시지 수")
            .tag("topic", topic)
            .register(registry)
            .increment();
    }

    public void markSchedulerSuccess(SchedulerTask task) {
        lastSuccessEpochSeconds.get(task).set(System.currentTimeMillis() / 1000);
    }

    private Counter orderPayments(String result, String reason) {
        return Counter.builder(ORDER_PAYMENTS)
            .description("주문 결제 결과. 실패면 reason에 사유 코드")
            .tag("result", result)
            .tag("reason", reason)
            .register(registry);
    }

    private static double ageSeconds(Supplier<Optional<LocalDateTime>> oldest) {
        return oldest.get()
            .map(createdAt -> (double) Math.max(0, Duration.between(createdAt, LocalDateTime.now()).toSeconds()))
            .orElse(0.0);
    }

    private static String tagValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
