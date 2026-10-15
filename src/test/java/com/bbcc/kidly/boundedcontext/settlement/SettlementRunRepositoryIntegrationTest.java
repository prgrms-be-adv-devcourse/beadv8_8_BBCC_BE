package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRun;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRunRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRunStatus;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class SettlementRunRepositoryIntegrationTest {

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Test
    @DisplayName("저장한 실행을 ID로 다시 읽는다")
    void saveAndFind() {
        var saved = settlementRunRepository.save(finishedRun());

        var found = settlementRunRepository.getById(saved.getId());

        assertThat(found.getPeriodStartOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(found.getPeriodEndOn()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(found.getStatus()).isEqualTo(SettlementRunStatus.COMPLETED);
    }

    @Test
    @DisplayName("없는 실행을 getById로 찾으면 NOT_FOUND다")
    void getByIdNotFound() {
        assertThatThrownBy(() -> settlementRunRepository.getById(Long.MAX_VALUE))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("실행 중인 정산은 하나만 저장된다")
    void oneRunning() {
        settlementRunRepository.save(SettlementFixture.run());

        assertThatThrownBy(() -> settlementRunRepository.save(SettlementFixture.run()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("첫 실행이 끝났으면 다음 실행 중 기록을 저장할 수 있다")
    void runningAfterFinish() {
        settlementRunRepository.save(finishedRun());

        var second = settlementRunRepository.save(SettlementFixture.run());

        assertThat(second.getId()).isNotNull();
    }

    private static SettlementRun finishedRun() {
        var run = SettlementFixture.run();
        run.finish(1, 0, NOW.plusMinutes(1));
        return run;
    }
}
