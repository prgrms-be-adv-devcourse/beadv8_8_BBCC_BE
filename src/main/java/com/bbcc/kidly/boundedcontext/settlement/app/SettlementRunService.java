package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementRunResult;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementPeriod;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
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
    private final SettlementSkipRepository settlementSkipRepository;

    // 기준일은 오늘 또는 지난 날짜. 그 날 0시 전에 확정된 대기 항목을 정산한다
    public SettlementRunResult runManually(LocalDate cutoffOn, Long adminId) {
        var period = SettlementPeriod.manual(cutoffOn, LocalDate.now());
        return run(period.cutoffAt(), SettlementTriggerType.MANUAL, adminId);
    }

    public SettlementRunResult runScheduled(LocalDate today) {
        return run(SettlementPeriod.monthly(today).cutoffAt(), SettlementTriggerType.SCHEDULED, null);
    }

    @Transactional(readOnly = true)
    public SettlementRunResult getRun(Long runId) {
        return resultOf(findRun(runId));
    }

    // 서버가 작업 도중 죽어 실행 중으로 남은 작업을 FAILED로 정리해, 같은 기준일로 다시 실행할 수 있게 한다.
    // 실제로 도는 작업과 구분하지 못하므로 서버를 다시 띄운 뒤에만 쓴다
    public SettlementRunResult recover(Long runId) {
        var execution = findRun(runId);
        if (!execution.getStatus().isRunning()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RUN_NOT_RECOVERABLE);
        }
        return resultOf(jobOperator.recover(execution));
    }

    // runId는 작업 실행 ID. JDBC 저장소는 없는 ID면 EmptyResultDataAccessException을 던진다
    private JobExecution findRun(Long runId) {
        JobExecution execution;
        try {
            execution = jobRepository.getJobExecution(runId);
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        if (execution == null || !execution.getJobInstance().getJobName().equals(SettlementJobConfig.JOB_NAME)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return execution;
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
            return resultOf(jobOperator.start(settlementJob, builder.toJobParameters()));
        } catch (JobInstanceAlreadyCompleteException e) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_RUN);
        } catch (JobExecutionAlreadyRunningException | DataIntegrityViolationException
            | OptimisticLockingFailureException e) {
            // 같은 기준일을 동시에 시작하면 작업 인스턴스 유니크 · 실행 버전 충돌로 늦은 쪽이 실패한다
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
        } catch (JobRestartException | InvalidJobParametersException e) {
            throw new IllegalStateException("정산 작업을 시작하지 못했습니다", e);
        }
    }

    private SettlementRunResult resultOf(JobExecution execution) {
        return SettlementRunResult.of(
            execution, settlementSkipRepository.findAllByJobExecutionIdOrderByIdAsc(execution.getId()));
    }
}
