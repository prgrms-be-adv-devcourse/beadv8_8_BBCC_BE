package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import com.bbcc.kidly.boundedcontext.order.app.OrderLogContext;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderScheduleIntervals;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderSchedulerProperties;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentExpiryGate;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.AutoConfirmTarget;
import com.bbcc.kidly.boundedcontext.order.app.port.SingleServerJobRunner;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주문의 시간 작업. 결제 마감 만료 취소 · 자동 구매확정을 맡긴다.
 * 주기는 설정(kidly.order.payment-expiry-interval · kidly.order.auto-confirm.interval, 기본값은 order.yml)으로 정하고,
 * 취소 · 확정해도 되는지는 주문이 직접 판단한다.
 * 한 실행은 batch-size건씩 마지막 처리 ID 다음부터 이어서 읽고(ID 커서), 덜 찬 배치가 오거나 max-per-run에 닿으면 끝난다.
 * 실패 건은 건너뛰고(커서는 지나감) 다음 실행에서 다시 시도한다. 작업마다 순환 커서({@link RotatingCursor}, #656)를 두어
 * 상한에 닿은 다음 실행은 멈춘 곳 다음부터 이어 읽는다(계속 실패하는 건이 상한 이상 앞쪽에 쌓여도 뒤쪽이 처리된다).
 * 상한에 닿은 실행은 실행 로그 한 줄을 WARN으로 남긴다(운영자가 상한을 늘릴지 판단).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    // 한 번에 한 서버에서만 돌 작업 이름(#564, 잠금 키 kidly.order.{작업})
    static final String AUTO_CONFIRM_JOB = "auto-confirm";
    static final String PAYMENT_EXPIRY_JOB = "payment-expiry";

    private final OrderService orderService;
    private final OrderSchedulerProperties properties;
    // 주기 값의 바인딩 검증(1초 이상)이 @Scheduled 등록보다 먼저 돌아 키 이름이 나오는 오류로 실패하게 하려고 주입한다
    private final OrderScheduleIntervals intervals;
    private final PaymentExpiryGate paymentExpiryGate;
    private final SingleServerJobRunner jobRunner;
    private final RotatingCursor autoConfirmCursor = new RotatingCursor();
    private final RotatingCursor paymentExpiryCursor = new RotatingCursor();

    /**
     * 배송 완료 후 기간이 지난 주문상품을 자동 구매확정한다. 커서는 주문상품 ID. 한 건 = 트랜잭션 하나,
     * 한 건이 실패(동시 변경 등)해도 로그만 남기고 다음 건을 처리한다.
     */
    @Scheduled(fixedDelayString = "${kidly.order.auto-confirm.interval}")
    public void autoConfirm() {
        jobRunner.run(AUTO_CONFIRM_JOB, this::autoConfirmTargets);
    }

    private void autoConfirmTargets() {
        // 기준 시각은 실행마다 한 번만 정해 모든 배치에 같게 쓴다(실행 중 새로 기간이 지난 건은 다음 실행에서)
        LocalDateTime now = LocalDateTime.now();
        RotatingCursor.Run run = runInBatches(autoConfirmCursor,
            (afterId, limit) -> orderService.findAutoConfirmTargets(now, afterId, limit),
            AutoConfirmTarget::orderItemId,
            AutoConfirmTarget::orderId,
            target -> {
                try {
                    orderService.confirmAutomatically(target.orderId(), target.orderItemId());
                    return true;
                } catch (RuntimeException e) {
                    log.warn("자동 구매확정 실패: orderId={} orderItemId={}", target.orderId(), target.orderItemId(), e);
                    return false;
                }
            });
        logRun("auto-confirm", run, autoConfirmCursor, intervals.autoConfirm().interval());
    }

    /**
     * 결제 마감이 지난 주문을 만료 취소한다. 커서는 주문 ID.
     * 결제 이벤트를 Kafka로 받는 중이면 처리 대기분이 없을 때만 돈다(#561, PaymentExpiryGate). 아니면 이번 주기를 건너뛴다.
     */
    @Scheduled(fixedDelayString = "${kidly.order.payment-expiry-interval}")
    public void expireOverduePayments() {
        jobRunner.run(PAYMENT_EXPIRY_JOB, this::expireOverduePaymentTargets);
    }

    // 잠금을 잡은 서버만 실행 조건(Kafka 대기량)을 본다: 서버마다 확인 · 건너뜀 로그가 겹치지 않는다
    private void expireOverduePaymentTargets() {
        if (!paymentExpiryGate.allowsRun()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        RotatingCursor.Run run = runInBatches(paymentExpiryCursor,
            (afterId, limit) -> orderService.findOverduePaymentOrderIds(now, afterId, limit),
            Function.identity(),
            Function.identity(),
            orderId -> {
                try {
                    orderService.cancelForPaymentExpiry(orderId);
                    return true;
                } catch (RuntimeException e) {
                    log.warn("결제 마감 만료 취소 실패: orderId={}", orderId, e);
                    return false;
                }
            });
        logRun("payment-expiry", run, paymentExpiryCursor, intervals.paymentExpiryInterval());
    }

    /**
     * 작업의 순환 커서 위치부터 limit = min(batch-size, 남은 상한)건씩 읽어 처리한다(끝에 닿으면 처음으로 돌아감, #656).
     * process는 성공이면 true, 실패(로그를 남긴 뒤)면 false를 돌려준다.
     * 대상 하나를 처리하는 동안 로그에 correlationId = 그 주문 ID를 붙인다(#603, 요청 밖이라 traceId도 주문 ID).
     */
    private <T> RotatingCursor.Run runInBatches(RotatingCursor cursor, BiFunction<Long, Integer, List<T>> fetch,
        Function<T, Long> idOf, Function<T, Long> orderIdOf, Function<T, Boolean> process) {
        return cursor.run(properties.batchSize(), properties.maxPerRun(), fetch, idOf, target -> {
            try (var ignored = OrderLogContext.open(null, null, String.valueOf(orderIdOf.apply(target)))) {
                return process.apply(target);
            }
        });
    }

    /**
     * 대상이 있으면 INFO, 없으면 DEBUG. 처리 = 예외 없이 끝난 건(적용 안 함 포함).
     * 상한에 닿았으면 같은 한 줄을 WARN으로 남기고 상한 · 다음 실행이 이어 읽을 위치를 붙인다(#656).
     */
    private void logRun(String job, RotatingCursor.Run run, RotatingCursor cursor, Duration interval) {
        if (run.targets() == 0) {
            log.debug("스케줄러 실행: job={} targets=0 interval={}", job, interval);
            return;
        }
        if (run.capped()) {
            log.warn("스케줄러 상한 도달: job={} targets={} processed={} failed={} batches={} maxPerRun={} "
                    + "resumeAfterId={} interval={}", job, run.targets(), run.targets() - run.failed(), run.failed(),
                run.batches(), properties.maxPerRun(), cursor.resumeAfterId(), interval);
            return;
        }
        log.info("스케줄러 실행: job={} targets={} processed={} failed={} batches={} interval={}", job, run.targets(),
            run.targets() - run.failed(), run.failed(), run.batches(), interval);
    }
}
