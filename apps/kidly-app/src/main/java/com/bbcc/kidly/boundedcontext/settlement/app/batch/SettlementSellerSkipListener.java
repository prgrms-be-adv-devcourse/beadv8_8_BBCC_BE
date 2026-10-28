package com.bbcc.kidly.boundedcontext.settlement.app.batch;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.settlement.app.support.SettlementSkipRecorder;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;

/**
 * 건너뛴 판매자를 로그와 DB에 남긴다. 그 판매자의 항목은 대기로 남아 다음 정산에서 다시 계산된다.
 * 기록은 보조 정보라 저장에 실패해도 정산을 멈추지 않는다(리스너 예외는 Batch가 단계 실패로 다룬다).
 */
@Slf4j
public class SettlementSellerSkipListener implements SkipListener<Long, SettlementSellerPlan> {

    private final SettlementSkipRecorder settlementSkipRecorder;
    private final Long jobExecutionId;

    public SettlementSellerSkipListener(SettlementSkipRecorder settlementSkipRecorder, Long jobExecutionId) {
        this.settlementSkipRecorder = requireNonNull(settlementSkipRecorder, "settlementSkipRecorder는 필수입니다");
        this.jobExecutionId = requireNonNull(jobExecutionId, "jobExecutionId는 필수입니다");
    }

    @Override
    public void onSkipInProcess(Long sellerId, Throwable t) {
        log.error("판매자 {}의 정산을 건너뜁니다", sellerId, t);
        record(sellerId, SettlementSkipPhase.PROCESS, t);
    }

    @Override
    public void onSkipInWrite(SettlementSellerPlan plan, Throwable t) {
        log.error("판매자 {}의 정산을 건너뜁니다", plan.sellerId(), t);
        record(plan.sellerId(), SettlementSkipPhase.WRITE, t);
    }

    private void record(Long sellerId, SettlementSkipPhase phase, Throwable t) {
        try {
            settlementSkipRecorder.record(jobExecutionId, sellerId, phase, t);
        } catch (RuntimeException e) {
            log.error("판매자 {}의 건너뛰기를 기록하지 못했습니다: jobExecutionId={}", sellerId, jobExecutionId, e);
        }
    }
}
