package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CUTOFF_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementRunResult;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkip;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipPhase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;

class SettlementRunResultTest {

    private static final long RUN_ID = 7L;

    @Test
    @DisplayName("완료이고 건너뛴 판매자가 없으면 COMPLETED다")
    void completed() {
        var result = result(BatchStatus.COMPLETED, 2, 0, 3L);

        assertThat(result.runId()).isEqualTo(RUN_ID);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failCount()).isZero();
        assertThat(result.cutoffAt()).isEqualTo(CUTOFF_AT);
        assertThat(result.triggerType()).isEqualTo("MANUAL");
        assertThat(result.requestedBy()).isEqualTo(3L);
    }

    @Test
    @DisplayName("완료이지만 건너뛴 판매자가 있으면 PARTIAL_FAILED다")
    void partialFailed() {
        var result = result(BatchStatus.COMPLETED, 1, 1, 3L);

        assertThat(result.status()).isEqualTo("PARTIAL_FAILED");
        assertThat(result.failCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("실행 중이면 RUNNING이다")
    void running() {
        assertThat(result(BatchStatus.STARTED, 0, 0, 3L).status()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("작업이 실패하면 FAILED다")
    void failed() {
        assertThat(result(BatchStatus.FAILED, 0, 0, 3L).status()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("관리자 ID가 없으면 requestedBy는 null이다")
    void requestedByAbsent() {
        assertThat(result(BatchStatus.COMPLETED, 0, 0, null).requestedBy()).isNull();
    }

    @Test
    @DisplayName("건너뛴 판매자를 판매자 ID · 단계 · 사유로 담는다")
    void skippedSellers() {
        var skip = SettlementSkip.create(new SettlementSkipCreateInfo(
            RUN_ID, 10L, SettlementSkipPhase.WRITE, "IllegalStateException: 정산 대상이 바뀌었습니다"));

        var result = SettlementRunResult.of(execution(BatchStatus.COMPLETED, 1, 1, 3L), List.of(skip));

        assertThat(result.skippedSellers()).singleElement()
            .isEqualTo(new SettlementRunResult.SkippedSeller(10L, "WRITE", "IllegalStateException: 정산 대상이 바뀌었습니다"));
    }

    private static SettlementRunResult result(BatchStatus status, long writeCount, long skipCount, Long requestedBy) {
        return SettlementRunResult.of(execution(status, writeCount, skipCount, requestedBy), List.of());
    }

    private static JobExecution execution(BatchStatus status, long writeCount, long skipCount, Long requestedBy) {
        var builder = new JobParametersBuilder().addLocalDateTime(SettlementJobConfig.CUTOFF_AT, CUTOFF_AT)
            .addString(SettlementJobConfig.TRIGGER_TYPE, "MANUAL", false);
        if (requestedBy != null) {
            builder.addLong(SettlementJobConfig.REQUESTED_BY, requestedBy, false);
        }
        var execution = new JobExecution(RUN_ID, new JobInstance(1L, SettlementJobConfig.JOB_NAME),
            builder.toJobParameters());
        execution.setStatus(status);
        var step = new StepExecution(1L, "settleSellerStep", execution);
        step.setWriteCount(writeCount);
        step.setWriteSkipCount(skipCount);
        execution.addStepExecution(step);
        return execution;
    }
}
