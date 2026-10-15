package com.bbcc.kidly.boundedcontext.settlement.app;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementRunResult;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementTriggerType;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 작업을 실행하고 실행 기록(Batch 작업 실행)을 조회한다. 작업은 묶음마다 자기 트랜잭션을 열어야 하므로
 * 트랜잭션을 걸지 않고, 끝날 때까지 기다린다(동기).
 */
@Service
@RequiredArgsConstructor
public class SettlementRunService {

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Job settlementJob;

    // 기준일은 오늘 또는 지난 날짜. 그 날 0시 전에 확정된 대기 항목을 정산한다
    public SettlementRunResult runManually(LocalDate cutoffOn, Long adminId) {
        requireNonNull(cutoffOn, "cutoffOn은 필수입니다");
        if (cutoffOn.isAfter(LocalDate.now())) {
            throw new BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_CUTOFF);
        }
        return run(cutoffOn.atStartOfDay(), SettlementTriggerType.MANUAL, adminId);
    }

    // 기준은 그 달 1일 0시라 지난달 확정분을 정산한다
    public SettlementRunResult runScheduled(LocalDate today) {
        requireNonNull(today, "today는 필수입니다");
        return run(today.withDayOfMonth(1).atStartOfDay(), SettlementTriggerType.SCHEDULED, null);
    }

    // runId는 작업 실행 ID. JDBC 저장소는 없는 ID면 EmptyResultDataAccessException을 던진다
    @Transactional(readOnly = true)
    public SettlementRunResult getRun(Long runId) {
        JobExecution execution;
        try {
            execution = jobRepository.getJobExecution(runId);
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        if (execution == null || !execution.getJobInstance().getJobName().equals(SettlementJobConfig.JOB_NAME)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return SettlementRunResult.from(execution);
    }

    // 실행 중 확인은 조회라 동시에 온 요청이 함께 통과할 수 있다. 같은 대상은 정산서 유니크 · 낙관적 락이 막는다
    private SettlementRunResult run(LocalDateTime cutoffAt, SettlementTriggerType triggerType, Long requestedBy) {
        var cutoff = new JobParametersBuilder().addLocalDateTime(SettlementJobConfig.CUTOFF_AT, cutoffAt)
            .toJobParameters();
        var last = jobRepository.getLastJobExecution(SettlementJobConfig.JOB_NAME, cutoff);
        if (last != null && last.getStatus() == BatchStatus.COMPLETED) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_RUN);
        }
        if (!jobRepository.findRunningJobExecutions(SettlementJobConfig.JOB_NAME).isEmpty()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
        }
        var builder = new JobParametersBuilder(cutoff)
            .addString(SettlementJobConfig.TRIGGER_TYPE, triggerType.name(), false);
        if (requestedBy != null) {
            builder.addLong(SettlementJobConfig.REQUESTED_BY, requestedBy, false);
        }
        try {
            return SettlementRunResult.from(jobOperator.start(settlementJob, builder.toJobParameters()));
        } catch (JobInstanceAlreadyCompleteException e) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_RUN);
        } catch (JobExecutionAlreadyRunningException e) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
        } catch (JobRestartException | InvalidJobParametersException e) {
            throw new IllegalStateException("정산 작업을 시작하지 못했습니다", e);
        }
    }
}
