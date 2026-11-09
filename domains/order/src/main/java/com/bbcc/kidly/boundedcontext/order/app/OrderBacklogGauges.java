package com.bbcc.kidly.boundedcontext.order.app;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderKind;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderService;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 주문 관리자 화면에 쌓이는 "사람이 처리할 일" 게이지(#1067): DLT 미처리 · 이벤트 실패 보관 미처리 · 결제 복구 대기 · 아웃박스 미전송 ·
 * 가장 오래된 미전송 나이 · 격리 · 멈춘 주문(관리자 summary와 같은 정의). 태그는 상태 · 종류 같은 고정 값만.
 * 게이지는 수집할 때마다 건수 · 최솟값만 읽는다(아웃박스는 미전송 부분 인덱스). 여러 쿼리인 멈춘 주문 요약만 짧게(30초) 캐시한다.
 */
@Component
public class OrderBacklogGauges {

    static final Duration STUCK_CACHE_TTL = Duration.ofSeconds(30);

    private final StuckOrderService stuckOrderService;
    private volatile Map<StuckOrderKind, Long> stuckSummary;
    private volatile long stuckSummaryAt;

    public OrderBacklogGauges(MeterRegistry registry, OrderDeadLetterRepository deadLetterRepository,
        OrderEventFailureRepository failureRepository, PaymentRecoveryRepository recoveryRepository,
        OrderOutboxRepository outboxRepository, StuckOrderService stuckOrderService) {
        this.stuckOrderService = stuckOrderService;
        Gauge.builder("kidly.order.dlt.messages",
                () -> deadLetterRepository.countPage(OrderDeadLetterRepository.Status.OPEN, null, null))
            .description("주문이 받은 DLT 메시지 중 아직 처리하지 않은(OPEN) 수")
            .tag("state", "open")
            .register(registry);
        Gauge.builder("kidly.order.event.failures",
                () -> failureRepository.countByStatus(OrderEventFailure.Status.PENDING))
            .description("재고 · 결제 이벤트 처리 실패 보관 중 아직 처리하지 않은(PENDING) 수")
            .tag("state", "pending")
            .register(registry);
        Gauge.builder("kidly.order.payment.recoveries",
                () -> recoveryRepository.countByStatus(PaymentRecovery.Status.PENDING))
            .description("결제 완료를 주문에 반영하지 못해 처리를 기다리는(PENDING) 결제 복구 수")
            .tag("state", "pending")
            .register(registry);
        Gauge.builder("kidly.order.outbox.unsent.messages", outboxRepository::countUnsent)
            .description("아직 Kafka로 보내지 않은 주문 아웃박스 메시지 수(격리 제외)")
            .register(registry);
        Gauge.builder("kidly.order.outbox.oldest.unsent.age.seconds", () -> outboxRepository.findOldestUnsentCreatedAt()
                .map(createdAt -> (double) Math.max(0, Duration.between(createdAt, LocalDateTime.now()).toSeconds()))
                .orElse(0.0))
            .description("가장 오래된 미전송 주문 아웃박스 메시지의 나이(초, 격리 제외). 없으면 0")
            .register(registry);
        Gauge.builder("kidly.order.outbox.quarantined.messages", outboxRepository::countQuarantined)
            .description("보내지 못하고 격리된 주문 아웃박스 메시지 수(관리자 재시도 전)")
            .register(registry);
        for (StuckOrderKind kind : StuckOrderKind.values()) {
            Gauge.builder("kidly.order.stuck.orders", () -> stuckSummary().getOrDefault(kind, 0L))
                .description("멈춘 주문 수(관리자 멈춘 주문 요약과 같은 정의, 종류별). 30초 캐시")
                .tag("kind", OrderOperationMetrics.tagValue(kind))
                .register(registry);
        }
    }

    private Map<StuckOrderKind, Long> stuckSummary() {
        long now = System.nanoTime();
        Map<StuckOrderKind, Long> cached = stuckSummary;
        if (cached == null || now - stuckSummaryAt >= STUCK_CACHE_TTL.toNanos()) {
            cached = stuckOrderService.getSummary(LocalDateTime.now());
            stuckSummary = cached;
            stuckSummaryAt = now;
        }
        return cached;
    }

    /**
     * 다음 수집이 멈춘 주문 요약을 다시 읽게 한다(테스트용).
     */
    void expireStuckCache() {
        stuckSummary = null;
    }
}
