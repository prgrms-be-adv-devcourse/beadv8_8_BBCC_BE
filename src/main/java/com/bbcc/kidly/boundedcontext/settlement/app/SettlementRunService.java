package com.bbcc.kidly.boundedcontext.settlement.app;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementRunResult;
import com.bbcc.kidly.boundedcontext.settlement.app.support.SettlementRunRecorder;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRunRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementTriggerType;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 작업을 실행한다. 작업은 묶음마다 자기 트랜잭션을 열어야 하므로 실행 메서드는 트랜잭션을 걸지 않는다.
 * 작업은 끝날 때까지 기다리고(동기), 작업 리스너가 마감한 실행 기록을 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class SettlementRunService {

    private final SettlementRunRepository settlementRunRepository;
    private final SettlementRunRecorder settlementRunRecorder;
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

    @Transactional(readOnly = true)
    public SettlementRunResult getRun(Long runId) {
        return SettlementRunResult.from(settlementRunRepository.getById(runId));
    }

    private SettlementRunResult run(LocalDateTime cutoffAt, SettlementTriggerType triggerType, Long requestedBy) {
        var cutoff = new JobParametersBuilder().addLocalDateTime(SettlementJobConfig.CUTOFF_AT, cutoffAt)
            .toJobParameters();
        var last = jobRepository.getLastJobExecution(SettlementJobConfig.JOB_NAME, cutoff);
        if (last != null && last.getStatus() == BatchStatus.COMPLETED) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_RUN);
        }
        Long runId;
        try {
            runId = settlementRunRecorder.start(cutoffAt, triggerType, requestedBy);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
        }
        var parameters = new JobParametersBuilder(cutoff).addLong(SettlementJobConfig.RUN_ID, runId, false)
            .toJobParameters();
        try {
            jobOperator.start(settlementJob, parameters);
        } catch (JobInstanceAlreadyCompleteException e) {
            settlementRunRecorder.fail(runId, 0, 0);
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_RUN);
        } catch (JobExecutionAlreadyRunningException e) {
            settlementRunRecorder.fail(runId, 0, 0);
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
        } catch (JobRestartException | InvalidJobParametersException e) {
            settlementRunRecorder.fail(runId, 0, 0);
            throw new IllegalStateException("정산 작업을 시작하지 못했습니다", e);
        } catch (RuntimeException e) {
            settlementRunRecorder.fail(runId, 0, 0);
            throw e;
        }
        return getRun(runId);
    }
}
