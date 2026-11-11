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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 주문 관리자 화면에 쌓이는 "사람이 처리할 일" 게이지(#1067): DLT 미처리 · 이벤트 실패 보관 미처리 · 결제 복구 대기 · 아웃박스 미전송 ·
 * 가장 오래된 미전송 나이 · 격리 · 멈춘 주문(관리자 summary와 같은 정의). 태그는 상태 · 종류 같은 고정 값만.
 * 게이지는 수집할 때마다 건수 · 최솟값만 읽는다(아웃박스는 미전송 부분 인덱스). 여러 쿼리인 멈춘 주문 요약만 짧게(30초) 캐시한다.
 * 요약 조회가 실패해도 시도 시각을 갱신해 30초 동안 다시 읽지 않고(#1222, 장애 때 수집마다 종류 7개가 각각 다시 조회하지 않게)
 * 마지막으로 성공한 값을 낸다. 한 번도 성공한 적이 없으면 NaN이다(0은 "멈춘 주문 없음"으로 읽히므로 쓰지 않는다).
 */
@Slf4j
@Component
public class OrderBacklogGauges {

    static final Duration STUCK_CACHE_TTL = Duration.ofSeconds(30);

    private final StuckOrderService stuckOrderService;
    // 마지막으로 성공한 요약(없으면 null) · 마지막 시도 시각(성공 · 실패 모두, nanoTime). 수집 스레드가 여럿이어도 창마다 한 번만 읽게 잠근다
    private Map<StuckOrderKind, Long> stuckSummary;
    private long stuckAttemptAt;
    private boolean stuckAttempted;
    // 지금 실패가 이어지는 중인지: 첫 실패만 WARN, 이어지는 실패는 DEBUG, 다시 성공하면 INFO 한 줄(복구)
    private boolean stuckFailing;

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
            Gauge.builder("kidly.order.stuck.orders", () -> stuckCount(kind))
                .description("멈춘 주문 수(관리자 멈춘 주문 요약과 같은 정의, 종류별). 30초 캐시, 조회 실패면 마지막 성공 값"
                    + "(성공한 적 없으면 NaN)")
                .tag("kind", OrderOperationMetrics.tagValue(kind))
                .register(registry);
        }
    }

    private double stuckCount(StuckOrderKind kind) {
        Map<StuckOrderKind, Long> summary = stuckSummary();
        return summary == null ? Double.NaN : summary.getOrDefault(kind, 0L);
    }

    private synchronized Map<StuckOrderKind, Long> stuckSummary() {
        long now = System.nanoTime();
        if (!stuckAttempted || now - stuckAttemptAt >= STUCK_CACHE_TTL.toNanos()) {
            stuckAttempted = true;
            stuckAttemptAt = now;
            try {
                stuckSummary = stuckOrderService.getSummary(LocalDateTime.now());
                if (stuckFailing) {
                    stuckFailing = false;
                    log.info("멈춘 주문 요약 게이지 조회 복구");
                }
            } catch (RuntimeException e) {
                // 성공(또는 처음) → 실패로 바뀐 첫 실패만 WARN, 이어지는 실패는 DEBUG(주문 CLAUDE.md 반복 로그 규칙). 메시지에는
                // 쿼리 · 값이 들어갈 수 있어 예외 클래스 이름만 남긴다
                if (stuckFailing) {
                    log.debug("멈춘 주문 요약 게이지 조회 실패 계속 — 마지막 성공 값 유지: exception={} hasPrevious={}",
                        e.getClass().getSimpleName(), stuckSummary != null);
                } else {
                    stuckFailing = true;
                    log.warn("멈춘 주문 요약 게이지 조회 실패 — 마지막 성공 값 유지: exception={} hasPrevious={}",
                        e.getClass().getSimpleName(), stuckSummary != null);
                }
            }
        }
        return stuckSummary;
    }

    /**
     * 다음 수집이 멈춘 주문 요약을 다시 읽게 한다(테스트용). 마지막 성공 값은 그대로 둔다.
     */
    synchronized void expireStuckCache() {
        stuckAttempted = false;
    }
}
