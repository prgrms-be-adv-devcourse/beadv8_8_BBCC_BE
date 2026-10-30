package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.port.PaymentEventLag;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentEventListenerState;
import com.bbcc.kidly.boundedcontext.order.domain.expiry.PaymentEventLagCheck;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 결제 마감 만료를 이번 주기에 실행해도 되는지 정한다(#561). 처리되지 않은 payment.completed가 남은 동안 만료가 돌면
 * 정상 결제된 주문을 결제 마감 만료로 취소할 수 있어서다.
 * <ul>
 *   <li>주문의 결제 이벤트 Kafka 리스너가 실행 중이 아니면(없음 · 멈춤 = Spring으로 받음) 확인 없이 실행</li>
 *   <li>실행 중이면 처리 대기량을 확인해 대기 0일 때만 실행. 대기 있음 · 확인 실패 · 배정 전이면 건너뛰고 INFO(이유 코드만)</li>
 *   <li>연속으로 건너뛴 시간이 10분을 넘으면 ERROR 한 번(만료가 멈춤), 다시 실행되면 INFO 한 줄과 함께 연속 기록을 지운다</li>
 * </ul>
 * 연속 기록은 이 빈의 메모리에 둔다(서버마다 만료 스케줄러가 돌고 각자 판단한다. 재시작하면 처음부터 센다).
 */
@Slf4j
@Component
public class PaymentExpiryGate {

    static final Duration ALERT_AFTER = Duration.ofMinutes(10);

    private final PaymentEventListenerState listener;
    private final PaymentEventLag lag;
    private final Clock clock;

    private Instant skippingSince;
    private boolean alerted;

    @Autowired
    public PaymentExpiryGate(PaymentEventListenerState listener, PaymentEventLag lag) {
        this(listener, lag, Clock.systemDefaultZone());
    }

    PaymentExpiryGate(PaymentEventListenerState listener, PaymentEventLag lag, Clock clock) {
        this.listener = listener;
        this.lag = lag;
        this.clock = clock;
    }

    /**
     * @return 이번 주기에 만료를 실행해도 되면 true
     */
    public synchronized boolean allowsRun() {
        if (!listener.isRunning()) {
            resume();
            return true;
        }
        PaymentEventLagCheck check = lag.check();
        if (check.status() == PaymentEventLagCheck.Status.CAUGHT_UP) {
            resume();
            return true;
        }
        skip(check);
        return false;
    }

    private void skip(PaymentEventLagCheck check) {
        Instant now = clock.instant();
        if (skippingSince == null) {
            skippingSince = now;
        }
        Duration skipped = Duration.between(skippingSince, now);
        // 저절로 맞춰지는 대기(처리 중인 결제 이벤트)는 조치가 필요 없어 INFO, 조치가 필요한 멈춤은 10분 ERROR가 알린다(#561-2)
        log.info("결제 마감 만료 건너뜀(결제 이벤트 처리 대기): reason={} lag={} skippedFor={}",
            reasonOf(check), check.lag(), skipped);
        if (!alerted && skipped.compareTo(ALERT_AFTER) > 0) {
            alerted = true;
            log.error("결제 마감 만료가 멈춤: 결제 이벤트 처리 대기로 {} 넘게 건너뜀 reason={} lag={}",
                ALERT_AFTER, reasonOf(check), check.lag());
        }
    }

    private void resume() {
        if (skippingSince == null) {
            return;
        }
        log.info("결제 마감 만료를 다시 실행: skippedFor={}", Duration.between(skippingSince, clock.instant()));
        skippingSince = null;
        alerted = false;
    }

    private static String reasonOf(PaymentEventLagCheck check) {
        return switch (check.status()) {
            case LAGGING -> "lag";
            case NOT_ASSIGNED -> "not-assigned";
            case CHECK_FAILED -> "check-failed:" + check.reason();
            case CAUGHT_UP -> "caught-up";
        };
    }
}
