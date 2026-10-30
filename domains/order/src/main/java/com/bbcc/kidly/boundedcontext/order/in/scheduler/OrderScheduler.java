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
 * 실패 건은 건너뛰고(커서는 지나감) 다음 실행에서 처음부터 다시 시도한다.
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
        Run run = runInBatches(
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
        logRun("auto-confirm", run, intervals.autoConfirm().interval());
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
        Run run = runInBatches(
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
        logRun("payment-expiry", run, intervals.paymentExpiryInterval());
    }

    /**
     * afterId = 0부터 limit = min(batch-size, 남은 상한)건씩 읽어 처리한다. 가져온 건수가 limit보다 적거나
     * max-per-run에 닿으면 끝낸다. process는 성공이면 true, 실패(로그를 남긴 뒤)면 false를 돌려준다.
     * 대상 하나를 처리하는 동안 로그에 correlationId = 그 주문 ID를 붙인다(#603, 요청 밖이라 traceId도 주문 ID).
     */
    private <T> Run runInBatches(BiFunction<Long, Integer, List<T>> fetch, Function<T, Long> idOf,
        Function<T, Long> orderIdOf, Function<T, Boolean> process) {
        long afterId = 0L;
        int targets = 0;
        int failed = 0;
        int batches = 0;
        while (targets < properties.maxPerRun()) {
            int limit = Math.min(properties.batchSize(), properties.maxPerRun() - targets);
            List<T> batch = fetch.apply(afterId, limit);
            batches++;
            for (T target : batch) {
                try (var ignored = OrderLogContext.open(null, null, String.valueOf(orderIdOf.apply(target)))) {
                    if (!process.apply(target)) {
                        failed++;
                    }
                }
            }
            targets += batch.size();
            if (batch.size() < limit) {
                break;
            }
            afterId = idOf.apply(batch.getLast());
        }
        return new Run(targets, failed, batches);
    }

    private record Run(int targets, int failed, int batches) {
    }

    /**
     * 대상이 있으면 INFO, 없으면 DEBUG. 처리 = 예외 없이 끝난 건(적용 안 함 포함).
     */
    private void logRun(String job, Run run, Duration interval) {
        if (run.targets() == 0) {
            log.debug("스케줄러 실행: job={} targets=0 interval={}", job, interval);
            return;
        }
        log.info("스케줄러 실행: job={} targets={} processed={} failed={} batches={} interval={}", job, run.targets(),
            run.targets() - run.failed(), run.failed(), run.batches(), interval);
    }
}
