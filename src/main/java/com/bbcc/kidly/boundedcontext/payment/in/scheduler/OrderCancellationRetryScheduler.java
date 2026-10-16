package com.bbcc.kidly.boundedcontext.payment.in.scheduler;

import com.bbcc.kidly.boundedcontext.payment.app.OrderCancellationRetryService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderCancellationRetryTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 처리하다 실패한 주문 취소를 다시 처리한다. 주기는 설정(kidly.payment.order-cancellation.retry-interval)으로 정한다.
 * 한 건 = 트랜잭션 하나. 한 건이 또 실패하면 시도 횟수를 남기고 다음 건을 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancellationRetryScheduler {

    private final OrderCancellationRetryService orderCancellationRetryService;

    @Scheduled(fixedDelayString = "${kidly.payment.order-cancellation.retry-interval:PT1M}")
    public void retryFailed() {
        for (OrderCancellationRetryTarget target : orderCancellationRetryService.findTargets()) {
            try {
                orderCancellationRetryService.retry(target);
                log.info("실패했던 주문 취소를 다시 처리했습니다. orderId={}", target.command().orderId());
            } catch (RuntimeException e) {
                log.error("주문 취소를 다시 처리하지 못했습니다. orderId={}", target.command().orderId(), e);
                orderCancellationRetryService.recordFailure(target.retryId(), e);
            }
        }
    }
}
