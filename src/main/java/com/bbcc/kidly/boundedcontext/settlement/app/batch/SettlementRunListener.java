package com.bbcc.kidly.boundedcontext.settlement.app.batch;

import com.bbcc.kidly.boundedcontext.settlement.app.support.SettlementRunRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * 작업이 끝나면 정산 실행 기록을 마감한다. 쓴 판매자 수가 성공, 건너뛴 판매자 수가 실패다.
 */
@Component
@RequiredArgsConstructor
public class SettlementRunListener implements JobExecutionListener {

    private final SettlementRunRecorder settlementRunRecorder;

    @Override
    public void afterJob(JobExecution jobExecution) {
        var runId = jobExecution.getJobParameters().getLong(SettlementJobConfig.RUN_ID);
        var steps = jobExecution.getStepExecutions();
        var successCount = Math.toIntExact(steps.stream().mapToLong(StepExecution::getWriteCount).sum());
        var failCount = Math.toIntExact(steps.stream().mapToLong(StepExecution::getSkipCount).sum());
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            settlementRunRecorder.finish(runId, successCount, failCount);
        } else {
            settlementRunRecorder.fail(runId, successCount, failCount);
        }
    }
}
