package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementRunResult;
import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementPeriod;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementTriggerType;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 작업을 실행하고 실행 기록(Batch 작업 실행)을 조회한다. 작업은 묶음마다 자기 트랜잭션을 열어야 하므로
 * 트랜잭션을 걸지 않고, 끝날 때까지 기다린다(동기). 서버가 실행 도중 죽어 STARTED로 남은 기록은 진행(step 갱신)이
 * {@link SettlementProperties#staleRunAfter()} 넘게 없으면 다음 실행이 FAILED로 닫고 진행한다(self-heal).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementRunService {

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Job settlementJob;
    private final SettlementSkipRepository settlementSkipRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementProperties properties;

    // 기준일은 오늘 또는 지난 날짜. 그 날 0시 전에 확정된 대기 항목을 정산한다
    public SettlementRunResult runManually(LocalDate cutoffOn, Long adminId) {
        var period = SettlementPeriod.manual(cutoffOn, LocalDate.now());
        return run(period.cutoffAt(), SettlementTriggerType.MANUAL, adminId);
    }

    /**
     * 끝나지 않은 실행이 있으면 그중 가장 먼저 시작한 것의 ID와 시작 시각. 없으면 비어 있다.
     * 서버가 실행 도중 죽으면 기록이 STARTED로 남아 그 뒤 실행이 "실행 중"으로 건너뛰어진다(진행 없이 staleRunAfter가 지나면
     * 다음 실행이 닫는다). 스케줄러가 이걸로 그래도 닫히지 않은 오래된 실행을 알린다.
     */
    public Optional<RunningExecution> findRunningSince() {
        return jobRepository.findRunningJobExecutions(SettlementJobConfig.JOB_NAME).stream()
            .filter(execution -> execution.getStartTime() != null)
            .min(Comparator.comparing(JobExecution::getStartTime))
            .map(execution -> new RunningExecution(execution.getId(), execution.getStartTime()));
    }

    public record RunningExecution(Long runId, LocalDateTime startedAt) {
    }

    // 오늘 0시를 기준으로 전날까지 확정된 대기 항목을 정산한다. 영업일인지는 호출한 쪽(스케줄러)이 확인한다
    public SettlementRunResult runScheduled(LocalDate today) {
        return run(SettlementPeriod.daily(today).cutoffAt(), SettlementTriggerType.SCHEDULED, null);
    }

    @Transactional(readOnly = true)
    public SettlementRunResult getRun(Long runId) {
        return resultOf(findRun(runId));
    }

    // 서버가 작업 도중 죽어 실행 중으로 남은 작업을 FAILED로 정리해, 같은 기준일로 다시 실행할 수 있게 한다.
    // 보통은 다음 실행이 진행 없는 기록을 스스로 닫는다(run). 이것은 그 시간을 기다리지 않고 닫을 때 쓰며,
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
        jobRepository.findRunningJobExecutions(SettlementJobConfig.JOB_NAME).forEach(this::recoverIfStale);
        var builder = new JobParametersBuilder(cutoff)
            .addString(SettlementJobConfig.TRIGGER_TYPE, triggerType.name(), false);
        if (requestedBy != null) {
            builder.addLong(SettlementJobConfig.REQUESTED_BY, requestedBy, false);
        }
        // 시작만 감싼다. 끝난 뒤의 조회 실패를 경쟁으로 잘못 판단하지 않게 한다
        JobExecution execution;
        try {
            execution = jobOperator.start(settlementJob, builder.toJobParameters());
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException | JobRestartException
            | InvalidJobParametersException | RuntimeException e) {
            throw conflictOr(cutoff, e);
        }
        return resultOf(execution);
    }

    // 진행 없이 staleRunAfter가 지난 실행은 죽은 것으로 보고 FAILED로 닫는다. 아니면 실행 중이다.
    // 닫는 사이 실제 작업이 step 기록을 갱신했으면 버전 충돌이 나므로 실행 중으로 본다(job 갱신은 버전을 맞추고 쓴다)
    private void recoverIfStale(JobExecution execution) {
        var lastActivity = lastActivityOf(execution);
        if (!lastActivity.isBefore(LocalDateTime.now().minus(properties.staleRunAfter()))) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
        }
        log.warn("정산 실행 {}이 {} 이후 진행이 없어 죽은 것으로 보고 FAILED로 닫습니다", execution.getId(), lastActivity);
        try {
            jobOperator.recover(execution);
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
        }
    }

    // step은 chunk 커밋마다 갱신되지만 job은 시작 · 끝에만 갱신된다. step 기록이 없으면 job 시작 시각,
    // 시작 전(STARTING)에 죽어 시작 시각도 없으면 생성 시각을 본다(생성 시각은 항상 있다)
    private static LocalDateTime lastActivityOf(JobExecution execution) {
        return execution.getStepExecutions().stream()
            .map(StepExecution::getLastUpdated)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .or(() -> Optional.ofNullable(execution.getStartTime()))
            .orElse(execution.getCreateTime());
    }

    // 같은 기준일을 동시에 시작하면 늦은 쪽은 Batch 안 어디서 끼었는지에 따라 다른 예외로 실패한다
    // (인스턴스 확인 Assert · 인스턴스 유니크 · 실행 버전 충돌 등). 예외 종류 대신 그 기준일 작업의 상태로
    // 경쟁에서 졌는지 판단하고, 상태로 설명되지 않으면 원래 예외를 던진다
    private RuntimeException conflictOr(JobParameters cutoff, Exception e) {
        var failure = e instanceof RuntimeException runtime
            ? runtime : new IllegalStateException("정산 작업을 시작하지 못했습니다", e);
        try {
            var instance = jobRepository.getJobInstance(SettlementJobConfig.JOB_NAME, cutoff);
            if (instance != null) {
                var last = jobRepository.getLastJobExecution(instance);
                // 실행이 없으면 다른 요청이 인스턴스를 만들고 실행을 만드는 중이다
                if (last == null || last.getStatus().isRunning()) {
                    return new BusinessException(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
                }
                if (last.getStatus() == BatchStatus.COMPLETED) {
                    return new BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_RUN);
                }
            }
        } catch (RuntimeException lookupFailure) {
            // DB 장애로 시작이 실패했다면 상태 조회도 함께 실패하기 쉽다. 조회 예외가 처음 원인을 덮지 않게 붙여 둔다
            failure.addSuppressed(lookupFailure);
        }
        return failure;
    }

    // 실행의 시작일은 그 실행이 만든 정산서(종료일이 같은 정산서) 중 가장 이른 시작일. 정산서가 없으면 종료일이다
    private SettlementRunResult resultOf(JobExecution execution) {
        var skips = settlementSkipRepository.findAllByJobExecutionIdOrderByIdAsc(execution.getId());
        var cutoffAt = execution.getJobParameters().getLocalDateTime(SettlementJobConfig.CUTOFF_AT);
        var endOn = SettlementPeriod.from(cutoffAt).endOn();
        // 같은 종료일의 정산서를 그 실행의 정산서로 본다. 실패한 실행을 복구해 같은 기준일로 다시 돌리면 앞선 실행 조회에도 재실행분이 섞여 보인다(표시값)
        var periodStartOn = settlementRepository.findFirstByPeriodEndOnOrderByPeriodStartOnAsc(endOn)
            .map(Settlement::getPeriodStartOn)
            .orElse(endOn);
        return SettlementRunResult.of(execution, periodStartOn, skips);
    }
}
