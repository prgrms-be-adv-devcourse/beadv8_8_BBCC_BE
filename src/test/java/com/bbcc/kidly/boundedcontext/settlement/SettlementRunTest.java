package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CUTOFF_AT;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRun;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRunStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementTriggerType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementRunTest {

    @Test
    @DisplayName("정산 실행을 시작하면 기준일에서 기간이 정해지고 실행 중이 된다")
    void start() {
        var run = SettlementRun.start(CUTOFF_AT, SettlementTriggerType.MANUAL, 3L, NOW);

        assertThat(run.getStatus()).isEqualTo(SettlementRunStatus.RUNNING);
        assertThat(run.getCutoffAt()).isEqualTo(CUTOFF_AT);
        assertThat(run.getPeriodStartOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(run.getPeriodEndOn()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(run.getTriggerType()).isEqualTo(SettlementTriggerType.MANUAL);
        assertThat(run.getRequestedBy()).isEqualTo(3L);
        assertThat(run.getSuccessCount()).isZero();
        assertThat(run.getFailCount()).isZero();
        assertThat(run.getStartedAt()).isEqualTo(NOW);
        assertThat(run.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("달 중간 기준일이면 그 달 1일부터 기준일 전날까지가 기간이다")
    void startMidMonth() {
        var run = SettlementRun.start(
            LocalDateTime.of(2026, 10, 16, 0, 0), SettlementTriggerType.SCHEDULED, null, NOW);

        assertThat(run.getPeriodStartOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(run.getPeriodEndOn()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    @DisplayName("1일 기준이면 지난달 전체가 기간이다")
    void startFirstDayCutoff() {
        var run = SettlementRun.start(
            LocalDateTime.of(2026, 10, 1, 0, 0), SettlementTriggerType.SCHEDULED, null, NOW);

        assertThat(run.getPeriodStartOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(run.getPeriodEndOn()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    @DisplayName("필수 값이 없으면 시작할 수 없다")
    void startRejectsNull() {
        assertThatThrownBy(() -> SettlementRun.start(null, SettlementTriggerType.SCHEDULED, null, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementRun.start(CUTOFF_AT, null, null, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SettlementRun.start(CUTOFF_AT, SettlementTriggerType.SCHEDULED, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("기준일이 0시가 아니면 시작할 수 없다")
    void startRejectsNotMidnight() {
        assertThatThrownBy(() -> SettlementRun.start(
            LocalDateTime.of(2026, 11, 1, 0, 0, 1), SettlementTriggerType.SCHEDULED, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("수동 실행은 관리자 ID가 있어야 하고 자동 실행은 없어야 한다")
    void startRejectsRequester() {
        assertThatThrownBy(() -> SettlementRun.start(CUTOFF_AT, SettlementTriggerType.MANUAL, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementRun.start(CUTOFF_AT, SettlementTriggerType.MANUAL, 0L, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementRun.start(CUTOFF_AT, SettlementTriggerType.SCHEDULED, 3L, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("건너뛴 판매자가 없으면 완료, 있으면 일부 실패로 끝낸다")
    void finish() {
        var completed = SettlementFixture.run();
        completed.finish(2, 0, NOW.plusMinutes(1));

        assertThat(completed.getStatus()).isEqualTo(SettlementRunStatus.COMPLETED);
        assertThat(completed.getSuccessCount()).isEqualTo(2);
        assertThat(completed.getFailCount()).isZero();
        assertThat(completed.getFinishedAt()).isEqualTo(NOW.plusMinutes(1));

        var partial = SettlementFixture.run();
        partial.finish(2, 1, NOW.plusMinutes(1));

        assertThat(partial.getStatus()).isEqualTo(SettlementRunStatus.PARTIAL_FAILED);
    }

    @Test
    @DisplayName("작업이 실패하면 실패로 끝낸다")
    void fail() {
        var run = SettlementFixture.run();
        run.fail(1, 0, NOW.plusMinutes(1));

        assertThat(run.getStatus()).isEqualTo(SettlementRunStatus.FAILED);
        assertThat(run.getFinishedAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    @DisplayName("이미 끝난 실행은 다시 끝낼 수 없다")
    void endRejectsNotRunning() {
        var run = SettlementFixture.run();
        run.finish(2, 0, NOW.plusMinutes(1));

        assertThatThrownBy(() -> run.finish(3, 0, NOW.plusMinutes(2))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.fail(3, 0, NOW.plusMinutes(2))).isInstanceOf(IllegalStateException.class);
        assertThat(run.getStatus()).isEqualTo(SettlementRunStatus.COMPLETED);
    }

    @Test
    @DisplayName("판매자 수가 음수면 끝낼 수 없다")
    void endRejectsNegativeCount() {
        var run = SettlementFixture.run();

        assertThatThrownBy(() -> run.finish(-1, 0, NOW.plusMinutes(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(run.getStatus()).isEqualTo(SettlementRunStatus.RUNNING);
    }
}
