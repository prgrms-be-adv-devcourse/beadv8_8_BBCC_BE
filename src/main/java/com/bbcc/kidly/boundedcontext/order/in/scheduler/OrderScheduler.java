package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.AutoConfirmTarget;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주문의 시간 작업. 결제 마감 만료 취소 · 자동 구매확정을 맡긴다.
 * 주기는 설정(kidly.order.payment-expiry-interval · kidly.order.auto-confirm.interval, 기본값은 order.yml)으로 정하고,
 * 취소 · 확정해도 되는지는 주문이 직접 판단한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderService orderService;

    /**
     * 배송 완료 후 기간이 지난 주문상품을 자동 구매확정한다. 한 건 = 트랜잭션 하나,
     * 한 건이 실패(동시 변경 등)해도 로그만 남기고 다음 건을 처리한다.
     */
    @Scheduled(fixedDelayString = "${kidly.order.auto-confirm.interval}")
    public void autoConfirm() {
        List<AutoConfirmTarget> targets = orderService.findAutoConfirmTargets();
        int failed = 0;
        for (AutoConfirmTarget target : targets) {
            try {
                orderService.confirmAutomatically(target.orderId(), target.orderItemId());
            } catch (RuntimeException e) {
                failed++;
                log.warn("자동 구매확정 실패: orderId={} orderItemId={}", target.orderId(), target.orderItemId(), e);
            }
        }
        logRun("auto-confirm", targets.size(), failed);
    }

    @Scheduled(fixedDelayString = "${kidly.order.payment-expiry-interval}")
    public void expireOverduePayments() {
        List<Long> orderIds = orderService.findOverduePaymentOrderIds();
        int failed = 0;
        for (Long orderId : orderIds) {
            try {
                orderService.cancelForPaymentExpiry(orderId);
            } catch (RuntimeException e) {
                failed++;
                log.warn("결제 마감 만료 취소 실패: orderId={}", orderId, e);
            }
        }
        logRun("payment-expiry", orderIds.size(), failed);
    }

    /**
     * 대상이 있으면 INFO, 없으면 DEBUG. 처리 = 예외 없이 끝난 건(적용 안 함 포함).
     */
    private void logRun(String job, int targets, int failed) {
        if (targets == 0) {
            log.debug("스케줄러 실행: job={} targets=0", job);
            return;
        }
        log.info("스케줄러 실행: job={} targets={} processed={} failed={}", job, targets, targets - failed, failed);
    }
}
