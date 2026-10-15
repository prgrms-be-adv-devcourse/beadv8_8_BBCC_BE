package com.bbcc.kidly.boundedcontext.settlement.app.dto;

import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementPeriod;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRunStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

/**
 * 정산 실행 결과. 정산 작업 실행(Batch 메타데이터)에서 만든다.
 *
 * @param id 작업 실행 ID
 * @param triggerType SCHEDULED · MANUAL
 * @param status RUNNING · COMPLETED · PARTIAL_FAILED · FAILED
 * @param successCount 정산서를 쓴 판매자 수
 * @param failCount 건너뛴 판매자 수
 * @param requestedBy 수동 실행한 관리자 ID. 자동 실행이면 null
 * @param finishedAt 끝나기 전에는 null
 */
public record SettlementRunResult(Long id, LocalDateTime cutoffAt, LocalDate periodStartOn, LocalDate periodEndOn,
    String triggerType, String status, int successCount, int failCount, Long requestedBy,
    LocalDateTime startedAt, LocalDateTime finishedAt) {

    public static SettlementRunResult from(JobExecution execution) {
        var parameters = execution.getJobParameters();
        var period = SettlementPeriod.from(parameters.getLocalDateTime(SettlementJobConfig.CUTOFF_AT));
        var steps = execution.getStepExecutions();
        var successCount = Math.toIntExact(steps.stream().mapToLong(StepExecution::getWriteCount).sum());
        var failCount = Math.toIntExact(steps.stream().mapToLong(StepExecution::getSkipCount).sum());
        return new SettlementRunResult(execution.getId(), period.cutoffAt(), period.startOn(), period.endOn(),
            parameters.getString(SettlementJobConfig.TRIGGER_TYPE), status(execution.getStatus(), failCount).name(),
            successCount, failCount, parameters.getLong(SettlementJobConfig.REQUESTED_BY),
            execution.getStartTime(), execution.getEndTime());
    }

    private static SettlementRunStatus status(BatchStatus status, int failCount) {
        return switch (status) {
            case COMPLETED -> failCount > 0 ? SettlementRunStatus.PARTIAL_FAILED : SettlementRunStatus.COMPLETED;
            case STARTING, STARTED, STOPPING -> SettlementRunStatus.RUNNING;
            default -> SettlementRunStatus.FAILED;
        };
    }
}
