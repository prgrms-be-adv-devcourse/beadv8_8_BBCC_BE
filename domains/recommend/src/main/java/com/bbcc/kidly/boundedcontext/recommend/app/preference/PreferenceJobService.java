package com.bbcc.kidly.boundedcontext.recommend.app.preference;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceJobRunLock;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.PreferenceJobConfig;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 선호 Job을 기준일로 실행하고 끝날 때까지 기다린다. 서버가 여러 대여도 잠금을 쥔 한 대만 실행한다.
 * chunk마다 자기 트랜잭션을 열어야 하므로 트랜잭션을 걸지 않는다.
 */
@Slf4j
@Service
public class PreferenceJobService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Job preferenceJob;
    private final PreferenceJobRunLock runLock;
    private final Duration staleAfter;

    public PreferenceJobService(JobOperator jobOperator, JobRepository jobRepository, Job preferenceJob,
        PreferenceJobRunLock runLock,
        @Value("${kidly.recommend.preference.stale-after:PT30M}") Duration staleAfter) {
        this.jobOperator = requireNonNull(jobOperator, "jobOperator는 필수입니다");
        this.jobRepository = requireNonNull(jobRepository, "jobRepository는 필수입니다");
        this.preferenceJob = requireNonNull(preferenceJob, "preferenceJob은 필수입니다");
        this.runLock = requireNonNull(runLock, "runLock은 필수입니다");
        this.staleAfter = requireNonNull(staleAfter, "staleAfter는 필수입니다");
    }

    // 새 기준일이 끝난 뒤 옛 기준일을 다시 돌리면 새 줄을 옛 계산으로 덮는다
    public PreferenceJobResult run(LocalDate baseDate) {
        if (!baseDate.equals(LocalDate.now(SERVICE_ZONE))) {
            throw new IllegalArgumentException("선호 Job 기준일은 오늘이어야 합니다: " + baseDate);
        }
        JobParameters parameters = new JobParametersBuilder()
            .addLocalDate(PreferenceJobConfig.BASE_DATE, baseDate)
            .toJobParameters();
        AtomicReference<PreferenceJobResult> result = new AtomicReference<>();
        if (!runLock.runExclusively(() -> result.set(runLocked(baseDate, parameters)))) {
            return PreferenceJobResult.LOCKED;
        }
        return result.get();
    }

    private PreferenceJobResult runLocked(LocalDate baseDate, JobParameters parameters) {
        JobExecution last = jobRepository.getLastJobExecution(PreferenceJobConfig.JOB_NAME, parameters);
        if (last != null && last.getStatus() == BatchStatus.COMPLETED) {
            return PreferenceJobResult.ALREADY_COMPLETED;
        }
        for (JobExecution running : jobRepository.findRunningJobExecutions(PreferenceJobConfig.JOB_NAME)) {
            if (!recoverIfStale(running)) {
                return PreferenceJobResult.IN_PROGRESS;
            }
        }
        JobExecution execution;
        try {
            execution = jobOperator.start(preferenceJob, parameters);
        } catch (JobInstanceAlreadyCompleteException e) {
            return PreferenceJobResult.ALREADY_COMPLETED;
        } catch (JobExecutionAlreadyRunningException e) {
            return PreferenceJobResult.IN_PROGRESS;
        } catch (JobRestartException | InvalidJobParametersException e) {
            throw new IllegalStateException("선호 Job을 시작하지 못했습니다: " + baseDate, e);
        }
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            return PreferenceJobResult.COMPLETED;
        }
        log.warn("선호 Job이 끝나지 못했습니다: baseDate={} executionId={} status={}", baseDate, execution.getId(),
            execution.getStatus());
        return PreferenceJobResult.FAILED;
    }

    // 잠금 연결이 유휴로 끊겨 잠금이 조용히 풀리면 다른 서버가 살아 있는 실행을 닫을 수 있다. 그래서 잠금을 쥐었어도
    // 진행이 staleAfter 넘게 없는 실행만 죽은 것으로 보고 FAILED로 닫는다. 닫는 사이 진행이 생기면 버전 충돌로 실행 중으로 본다
    private boolean recoverIfStale(JobExecution execution) {
        LocalDateTime lastActivity = lastActivityOf(execution);
        if (!lastActivity.isBefore(LocalDateTime.now().minus(staleAfter))) {
            return false;
        }
        log.warn("선호 Job 실행 {}이 {} 이후 진행이 없어 FAILED로 닫습니다", execution.getId(), lastActivity);
        try {
            jobOperator.recover(execution);
        } catch (OptimisticLockingFailureException e) {
            return false;
        }
        return true;
    }

    // Step은 chunk 커밋마다 갱신되지만 Job은 시작과 끝에만 갱신된다. Step 기록이 없으면 시작 시각, 그것도 없으면 생성 시각을 본다
    private static LocalDateTime lastActivityOf(JobExecution execution) {
        LocalDateTime lastActivity = null;
        for (StepExecution step : execution.getStepExecutions()) {
            LocalDateTime updated = step.getLastUpdated();
            if (updated != null && (lastActivity == null || updated.isAfter(lastActivity))) {
                lastActivity = updated;
            }
        }
        if (lastActivity != null) {
            return lastActivity;
        }
        if (execution.getStartTime() != null) {
            return execution.getStartTime();
        }
        return execution.getCreateTime();
    }
}
