package com.bbcc.kidly.boundedcontext.settlement.app.support;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRun;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRunRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementTriggerType;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 실행 기록을 시작 · 마감한다. 작업은 트랜잭션 밖에서 돌아야 해서 기록만 따로 트랜잭션을 연다.
 */
@Component
@RequiredArgsConstructor
public class SettlementRunRecorder {

    private final SettlementRunRepository settlementRunRepository;

    // 실행 중인 기록이 이미 있으면 running_key 유니크 위반으로 실패한다
    @Transactional
    public Long start(LocalDateTime cutoffAt, SettlementTriggerType triggerType, Long requestedBy) {
        return settlementRunRepository.save(
            SettlementRun.start(cutoffAt, triggerType, requestedBy, LocalDateTime.now())).getId();
    }

    @Transactional
    public void finish(Long runId, int successCount, int failCount) {
        settlementRunRepository.getById(runId).finish(successCount, failCount, LocalDateTime.now());
    }

    @Transactional
    public void fail(Long runId, int successCount, int failCount) {
        settlementRunRepository.getById(runId).fail(successCount, failCount, LocalDateTime.now());
    }
}
