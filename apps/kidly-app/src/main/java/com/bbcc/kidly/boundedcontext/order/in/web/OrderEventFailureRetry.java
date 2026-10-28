package com.bbcc.kidly.boundedcontext.order.in.web;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderEventFailureService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentFailures;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderEventFailureResult;
import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 관리자 이벤트 처리 실패 재처리(#449). 결제 반영 재처리(PaymentRecoveryRetry)와 같은 이유로 트랜잭션을 걸지 않는다:
 * 재처리 트랜잭션이 실패하면 PostgreSQL이 그 트랜잭션을 더 쓸 수 없게 하므로, 전부 되돌려진 뒤 시도 횟수는
 * OrderEventFailureService.recordRetryFailure의 새 트랜잭션에서 센다.
 */
@Component
@RequiredArgsConstructor
public class OrderEventFailureRetry {

    private final OrderEventFailureService failureService;

    /**
     * 업무 오류(없음 · 종료됨)와 동시 처리에서 진 쪽(낙관적 락)은 시도 횟수를 세지 않고 그대로 던진다(진 쪽은 409).
     * 그 밖의 실패는 센 기록을 돌려준다(200, 상태 PENDING).
     */
    public OrderEventFailureResult retry(Long failureId, Long adminId) {
        try {
            return failureService.recover(failureId, adminId);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            if (PaymentFailures.isLostConcurrentDuplicate(e)) {
                throw e;
            }
            return failureService.recordRetryFailure(failureId, e);
        }
    }
}
