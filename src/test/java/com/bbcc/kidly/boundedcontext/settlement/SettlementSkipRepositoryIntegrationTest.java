package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkip;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipPhase;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipRepository;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class SettlementSkipRepositoryIntegrationTest {

    @Autowired
    private SettlementSkipRepository settlementSkipRepository;

    @Test
    @DisplayName("한 실행에서 같은 판매자는 한 번만 기록된다. 다른 실행이면 다시 기록된다")
    void oncePerRunAndSeller() {
        settlementSkipRepository.save(skip(7L, 10L));

        assertThatThrownBy(() -> settlementSkipRepository.save(skip(7L, 10L)))
            .isInstanceOf(DataIntegrityViolationException.class);
        settlementSkipRepository.save(skip(8L, 10L));
        assertThat(settlementSkipRepository.findAllByJobExecutionIdOrderByIdAsc(7L)).hasSize(1);
        assertThat(settlementSkipRepository.findAllByJobExecutionIdOrderByIdAsc(8L)).hasSize(1);
    }

    private static SettlementSkip skip(Long jobExecutionId, Long sellerId) {
        return SettlementSkip.create(
            new SettlementSkipCreateInfo(jobExecutionId, sellerId, SettlementSkipPhase.WRITE, "IllegalStateException"));
    }
}
