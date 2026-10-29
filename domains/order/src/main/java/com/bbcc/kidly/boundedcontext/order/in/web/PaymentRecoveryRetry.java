package com.bbcc.kidly.boundedcontext.order.in.web;

import com.bbcc.kidly.boundedcontext.order.app.OrderActor;
import com.bbcc.kidly.boundedcontext.order.app.PaymentCompletionService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentFailures;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentRecoveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PaymentRecoveryResult;
import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 관리자 재처리(#353). 일부러 트랜잭션을 걸지 않는다: 재처리 트랜잭션이 실패하면 PostgreSQL이 그 트랜잭션을 더 쓸 수 없게
 * 하므로, 전부 되돌려진 뒤 시도 횟수는 PaymentRecoveryService.recordRetryFailure의 새 트랜잭션에서 센다.
 * 결제 완료 반영은 리스너와 같은 PaymentCompletionService를 재처리 트랜잭션 안에서 실행한다(합류).
 */
@Component
@RequiredArgsConstructor
public class PaymentRecoveryRetry {

    private final PaymentRecoveryService recoveryService;
    private final PaymentCompletionService paymentCompletion;

    /**
     * 업무 오류(없음 · 종료됨 · 재처리 불가)와 동시 처리에서 진 쪽(낙관적 락 · 배송 유니크)은 시도 횟수를 세지 않고
     * 그대로 던진다(진 쪽은 이긴 쪽이 처리했으므로 409). 그 밖의 실패는 센 기록을 돌려준다(200, 상태 PENDING).
     */
    public PaymentRecoveryResult retry(Long recoveryId, Long adminId) {
        try {
            return recoveryService.recover(recoveryId, adminId,
                paid -> paymentCompletion.apply(paid, OrderActor.admin(adminId, OrderActor.RECOVERY_RETRY)));
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            if (PaymentFailures.isLostConcurrentDuplicate(e)) {
                throw e;
            }
            return recoveryService.recordRetryFailure(recoveryId, adminId, e);
        }
    }
}
