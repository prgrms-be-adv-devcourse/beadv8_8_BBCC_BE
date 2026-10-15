package com.bbcc.kidly.boundedcontext.settlement;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementRunListener;
import com.bbcc.kidly.boundedcontext.settlement.app.support.SettlementRunRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;

@ExtendWith(MockitoExtension.class)
class SettlementRunListenerTest {

    private static final long RUN_ID = 7L;

    @Mock
    private SettlementRunRecorder settlementRunRecorder;

    @InjectMocks
    private SettlementRunListener settlementRunListener;

    @Test
    @DisplayName("작업이 실패하면 쓴 판매자 수 · 건너뛴 판매자 수로 실행을 실패 처리한다")
    void failedJob() {
        var jobExecution = jobExecution(BatchStatus.FAILED, 3, 1);

        settlementRunListener.afterJob(jobExecution);

        verify(settlementRunRecorder).fail(RUN_ID, 3, 1);
        verify(settlementRunRecorder, never()).finish(RUN_ID, 3, 1);
    }

    @Test
    @DisplayName("작업이 끝나면 쓴 판매자 수 · 건너뛴 판매자 수로 실행을 마감한다")
    void completedJob() {
        var jobExecution = jobExecution(BatchStatus.COMPLETED, 2, 1);

        settlementRunListener.afterJob(jobExecution);

        verify(settlementRunRecorder).finish(RUN_ID, 2, 1);
    }

    private static JobExecution jobExecution(BatchStatus status, long writeCount, long writeSkipCount) {
        var parameters = new JobParametersBuilder().addLong(SettlementJobConfig.RUN_ID, RUN_ID, false)
            .toJobParameters();
        var jobExecution = new JobExecution(1L, new JobInstance(1L, SettlementJobConfig.JOB_NAME), parameters);
        jobExecution.setStatus(status);
        var stepExecution = new StepExecution(1L, "settleSellerStep", jobExecution);
        stepExecution.setWriteCount(writeCount);
        stepExecution.setWriteSkipCount(writeSkipCount);
        jobExecution.addStepExecution(stepExecution);
        return jobExecution;
    }
}
