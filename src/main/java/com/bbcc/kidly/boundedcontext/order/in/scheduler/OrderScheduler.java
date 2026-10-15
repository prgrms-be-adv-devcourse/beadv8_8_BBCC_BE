package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderSchedulerProperties;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.AutoConfirmTarget;
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

    private final OrderService orderService;
    private final OrderSchedulerProperties properties;

    /**
     * 배송 완료 후 기간이 지난 주문상품을 자동 구매확정한다. 커서는 주문상품 ID. 한 건 = 트랜잭션 하나,
     * 한 건이 실패(동시 변경 등)해도 로그만 남기고 다음 건을 처리한다.
     */
    @Scheduled(fixedDelayString = "${kidly.order.auto-confirm.interval}")
    public void autoConfirm() {
        // 기준 시각은 실행마다 한 번만 정해 모든 배치에 같게 쓴다(실행 중 새로 기간이 지난 건은 다음 실행에서)
        LocalDateTime now = LocalDateTime.now();
        Run run = runInBatches(
            (afterId, limit) -> orderService.findAutoConfirmTargets(now, afterId, limit),
            AutoConfirmTarget::orderItemId,
            target -> {
                try {
                    orderService.confirmAutomatically(target.orderId(), target.orderItemId());
                    return true;
                } catch (RuntimeException e) {
                    log.warn("자동 구매확정 실패: orderId={} orderItemId={}", target.orderId(), target.orderItemId(), e);
                    return false;
                }
            });
        logRun("auto-confirm", run);
    }

    /**
     * 결제 마감이 지난 주문을 만료 취소한다. 커서는 주문 ID.
     */
    @Scheduled(fixedDelayString = "${kidly.order.payment-expiry-interval}")
    public void expireOverduePayments() {
        LocalDateTime now = LocalDateTime.now();
        Run run = runInBatches(
            (afterId, limit) -> orderService.findOverduePaymentOrderIds(now, afterId, limit),
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
        logRun("payment-expiry", run);
    }

    /**
     * afterId = 0부터 limit = min(batch-size, 남은 상한)건씩 읽어 처리한다. 가져온 건수가 limit보다 적거나
     * max-per-run에 닿으면 끝낸다. process는 성공이면 true, 실패(로그를 남긴 뒤)면 false를 돌려준다.
     */
    private <T> Run runInBatches(BiFunction<Long, Integer, List<T>> fetch, Function<T, Long> idOf,
        Function<T, Boolean> process) {
        long afterId = 0L;
        int targets = 0;
        int failed = 0;
        int batches = 0;
        while (targets < properties.maxPerRun()) {
            int limit = Math.min(properties.batchSize(), properties.maxPerRun() - targets);
            List<T> batch = fetch.apply(afterId, limit);
            batches++;
            for (T target : batch) {
                if (!process.apply(target)) {
                    failed++;
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
    private void logRun(String job, Run run) {
        if (run.targets() == 0) {
            log.debug("스케줄러 실행: job={} targets=0", job);
            return;
        }
        log.info("스케줄러 실행: job={} targets={} processed={} failed={} batches={}", job, run.targets(),
            run.targets() - run.failed(), run.failed(), run.batches());
    }
}
