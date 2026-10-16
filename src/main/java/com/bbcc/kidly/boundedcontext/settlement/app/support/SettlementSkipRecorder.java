package com.bbcc.kidly.boundedcontext.settlement.app.support;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkip;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipPhase;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 건너뛴 판매자를 기록한다. 건너뛰기는 묶음 트랜잭션 안에서 알려지므로, 묶음이 롤백돼도 남도록 새 트랜잭션에서 저장한다.
 */
@Component
@RequiredArgsConstructor
public class SettlementSkipRecorder {

    private final SettlementSkipRepository settlementSkipRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long jobExecutionId, Long sellerId, SettlementSkipPhase phase, Throwable error) {
        settlementSkipRepository.save(
            SettlementSkip.create(new SettlementSkipCreateInfo(jobExecutionId, sellerId, phase, error)));
    }
}
