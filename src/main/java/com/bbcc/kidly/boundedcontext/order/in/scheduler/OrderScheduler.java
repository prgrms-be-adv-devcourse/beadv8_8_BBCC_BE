package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 마감이 지난 결제대기 주문을 취소한다. 주기는 설정(kidly.order.payment-expiry-interval)으로 정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderService orderService;

    /**
     * 한 건 = 트랜잭션 하나. 한 건이 실패(동시 변경 등)해도 로그만 남기고 다음 건을 처리한다.
     */
    @Scheduled(fixedDelayString = "${kidly.order.payment-expiry-interval:PT1M}")
    public void expireOverduePayments() {
        for (Long orderId : orderService.findOverduePaymentOrderIds()) {
            try {
                orderService.cancelForPaymentExpiry(orderId);
            } catch (RuntimeException e) {
                log.warn("결제 마감 만료 취소 실패: orderId={}", orderId, e);
            }
        }
    }
}
