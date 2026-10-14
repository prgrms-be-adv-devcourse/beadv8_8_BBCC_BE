package com.bbcc.kidly.boundedcontext.payment.in.scheduler;

import com.bbcc.kidly.boundedcontext.payment.app.ChargeReconcileService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeReconcileTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 충전 정리 스케줄러. 주기는 설정(kidly.payment.charge.reconcile-interval)으로 정한다.
 * 한 건 = 트랜잭션 하나. 한 건이 실패(동시 변경 등)해도 로그만 남기고 다음 건을 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargeScheduler {

    private final ChargeReconcileService chargeReconcileService;

    @Scheduled(fixedDelayString = "${kidly.payment.charge.reconcile-interval:PT1M}")
    public void reconcileConfirming() {
        for (ChargeReconcileTarget target : chargeReconcileService.findStaleConfirming()) {
            try {
                chargeReconcileService.reconcile(target);
            } catch (RuntimeException e) {
                log.warn("승인 중 충전 정리 실패: chargeId={}", target.chargeId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${kidly.payment.charge.reconcile-interval:PT1M}")
    public void expireOverduePending() {
        for (Long chargeId : chargeReconcileService.findOverduePendingIds()) {
            try {
                chargeReconcileService.expire(chargeId);
            } catch (RuntimeException e) {
                log.warn("대기 충전 만료 실패: chargeId={}", chargeId, e);
            }
        }
    }
}
