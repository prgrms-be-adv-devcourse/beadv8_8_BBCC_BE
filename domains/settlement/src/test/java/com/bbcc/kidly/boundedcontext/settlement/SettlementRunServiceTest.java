package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementProperties;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class SettlementRunServiceTest {

    private static final JobInstance INSTANCE = new JobInstance(1L, SettlementJobConfig.JOB_NAME);

    @Mock
    private JobOperator jobOperator;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private Job settlementJob;

    @Mock
    private SettlementSkipRepository settlementSkipRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Spy
    private SettlementProperties properties = new SettlementProperties(
        new BigDecimal("0.1"), "0 5 9-17 * * MON-FRI", "0 */10 * * * *", 100, Duration.ofMinutes(30));

    @InjectMocks
    private SettlementRunService settlementRunService;

    @Test
    @DisplayName("시작이 실패했는데 그 기준일 작업이 없으면 원래 예외를 그대로 던진다")
    void startFailure() throws Exception {
        var failure = new DataAccessResourceFailureException("DB 연결 실패");
        startFails(failure);

        assertThatThrownBy(this::runScheduled).isSameAs(failure);
    }

    @Test
    @DisplayName("시작이 실패한 뒤 작업 상태 조회도 실패하면 원래 예외를 던지고 조회 예외는 함께 붙여 둔다")
    void lookupFailureKeepsCause() throws Exception {
        var failure = new DataAccessResourceFailureException("DB 연결 실패");
        startFails(failure);
        var lookupFailure = new DataAccessResourceFailureException("상태 조회 실패");
        given(jobRepository.getJobInstance(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willThrow(lookupFailure);

        assertThatThrownBy(this::runScheduled).isSameAs(failure)
            .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(lookupFailure));
    }

    @Test
    @DisplayName("시작이 실패했는데 그 기준일 작업이 실행 중이면 경쟁에서 진 것이라 SETTLEMENT_RUN_IN_PROGRESS다")
    void lostToRunning() throws Exception {
        startFails(new IllegalStateException("JobInstance must not already exist"));
        givenLastExecution(BatchStatus.STARTED);

        assertErrorCode(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
    }

    @Test
    @DisplayName("시작이 실패했는데 그 기준일 작업 인스턴스만 있고 실행이 아직 없으면 SETTLEMENT_RUN_IN_PROGRESS다")
    void lostToStarting() throws Exception {
        startFails(new DuplicateKeyException("uk_job_inst"));
        given(jobRepository.getJobInstance(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willReturn(INSTANCE);
        given(jobRepository.getLastJobExecution(INSTANCE)).willReturn(null);

        assertErrorCode(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
    }

    @Test
    @DisplayName("시작이 실패했는데 그 기준일 작업이 이미 완료됐으면 SETTLEMENT_ALREADY_RUN이다")
    void lostToCompleted() throws Exception {
        startFails(new JobInstanceAlreadyCompleteException("완료된 인스턴스"));
        givenLastExecution(BatchStatus.COMPLETED);

        assertErrorCode(SettlementErrorCode.SETTLEMENT_ALREADY_RUN);
    }

    @Test
    @DisplayName("시작이 실패했는데 그 기준일 작업이 실패로 끝나 있으면 경쟁으로 설명되지 않아 원래 예외를 던진다")
    void failedInstance() throws Exception {
        var failure = new DataAccessResourceFailureException("DB 연결 실패");
        startFails(failure);
        givenLastExecution(BatchStatus.FAILED);

        assertThatThrownBy(this::runScheduled).isSameAs(failure);
    }

    @Test
    @DisplayName("작업이 끝난 뒤 결과 조회가 실패하면 경쟁으로 판단하지 않고 그 예외를 던진다")
    void resultLookupFailure() throws Exception {
        given(jobRepository.getLastJobExecution(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willReturn(null);
        given(jobRepository.findRunningJobExecutions(SettlementJobConfig.JOB_NAME)).willReturn(Set.of());
        var completed = new JobExecution(1L, INSTANCE, new JobParameters());
        completed.setStatus(BatchStatus.COMPLETED);
        given(jobOperator.start(eq(settlementJob), any(JobParameters.class))).willReturn(completed);
        var failure = new DataAccessResourceFailureException("DB 연결 실패");
        given(settlementSkipRepository.findAllByJobExecutionIdOrderByIdAsc(1L)).willThrow(failure);
        // 경쟁으로 판단했다면 이 상태를 읽고 SETTLEMENT_ALREADY_RUN을 던졌을 것이다
        lenient().when(jobRepository.getJobInstance(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .thenReturn(INSTANCE);
        lenient().when(jobRepository.getLastJobExecution(INSTANCE)).thenReturn(completed);

        assertThatThrownBy(this::runScheduled).isSameAs(failure);
    }

    @Test
    @DisplayName("자동 실행은 오늘 0시를 기준으로 하고, 실행의 시작일은 그 종료일 정산서 중 가장 이른 시작일이다")
    void scheduledUsesTodayAndEarliestStart() throws Exception {
        var cutoffAt = LocalDateTime.of(2026, 11, 2, 0, 0);
        givenCompletedRun(cutoffAt);
        given(settlementRepository.findFirstByPeriodEndOnOrderByPeriodStartOnAsc(LocalDate.of(2026, 11, 1)))
            .willReturn(Optional.of(SettlementFixture.settlement(
                10L, LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 1))));

        var result = settlementRunService.runScheduled(LocalDate.of(2026, 11, 2));

        assertThat(result.cutoffAt()).isEqualTo(cutoffAt);
        assertThat(result.periodStartOn()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(result.periodEndOn()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("그 실행이 만든 정산서가 없으면 실행의 시작일은 종료일이다")
    void startOnFallsBackToEndOn() throws Exception {
        givenCompletedRun(LocalDateTime.of(2026, 11, 2, 0, 0));
        given(settlementRepository.findFirstByPeriodEndOnOrderByPeriodStartOnAsc(LocalDate.of(2026, 11, 1)))
            .willReturn(Optional.empty());

        var result = settlementRunService.runScheduled(LocalDate.of(2026, 11, 2));

        assertThat(result.periodStartOn()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("실행 중 기록의 step이 30분 넘게 갱신되지 않았으면 FAILED로 닫고 정산을 시작한다")
    void staleRunRecovered() throws Exception {
        var stale = runningExecution(LocalDateTime.now().minusMinutes(31));
        given(jobRepository.getLastJobExecution(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willReturn(null);
        given(jobRepository.findRunningJobExecutions(SettlementJobConfig.JOB_NAME)).willReturn(Set.of(stale));
        var completed = new JobExecution(2L, INSTANCE, new JobParametersBuilder()
            .addLocalDateTime(SettlementJobConfig.CUTOFF_AT, LocalDateTime.of(2026, 11, 15, 0, 0)).toJobParameters());
        completed.setStatus(BatchStatus.COMPLETED);
        given(jobOperator.start(eq(settlementJob), any(JobParameters.class))).willReturn(completed);

        var result = settlementRunService.runScheduled(LocalDate.of(2026, 11, 15));

        verify(jobOperator).recover(stale);
        assertThat(result.runId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("시작 전(STARTING)에 죽어 step도 시작 시각도 없는 기록은 생성 30분 뒤부터 FAILED로 닫고 정산을 시작한다")
    void staleStartingRunRecovered() throws Exception {
        var stale = new JobExecution(1L, INSTANCE, new JobParameters());
        stale.setStatus(BatchStatus.STARTING);
        stale.setCreateTime(LocalDateTime.now().minusMinutes(31));
        given(jobRepository.getLastJobExecution(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willReturn(null);
        given(jobRepository.findRunningJobExecutions(SettlementJobConfig.JOB_NAME)).willReturn(Set.of(stale));
        var completed = new JobExecution(2L, INSTANCE, new JobParametersBuilder()
            .addLocalDateTime(SettlementJobConfig.CUTOFF_AT, LocalDateTime.of(2026, 11, 15, 0, 0)).toJobParameters());
        completed.setStatus(BatchStatus.COMPLETED);
        given(jobOperator.start(eq(settlementJob), any(JobParameters.class))).willReturn(completed);

        var result = settlementRunService.runScheduled(LocalDate.of(2026, 11, 15));

        verify(jobOperator).recover(stale);
        assertThat(result.runId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("오래된 실행을 닫는 사이 실제 작업이 기록을 갱신했으면(버전 충돌) SETTLEMENT_RUN_IN_PROGRESS다")
    void staleRunUpdatedMeanwhile() throws Exception {
        var stale = runningExecution(LocalDateTime.now().minusMinutes(31));
        given(jobRepository.getLastJobExecution(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willReturn(null);
        given(jobRepository.findRunningJobExecutions(SettlementJobConfig.JOB_NAME)).willReturn(Set.of(stale));
        given(jobOperator.recover(stale)).willThrow(new OptimisticLockingFailureException("버전 충돌"));

        assertErrorCode(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("실행 중 기록의 step이 30분 안에 갱신됐으면 닫지 않고 SETTLEMENT_RUN_IN_PROGRESS다")
    void recentRunInProgress() throws Exception {
        var running = runningExecution(LocalDateTime.now().minusMinutes(29));
        given(jobRepository.getLastJobExecution(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willReturn(null);
        given(jobRepository.findRunningJobExecutions(SettlementJobConfig.JOB_NAME)).willReturn(Set.of(running));

        assertErrorCode(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
        verify(jobOperator, never()).recover(any(JobExecution.class));
        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    // 1시간 전에 시작해 step이 stepLastUpdated에 마지막으로 갱신된 실행 중 기록
    private static JobExecution runningExecution(LocalDateTime stepLastUpdated) {
        var execution = new JobExecution(1L, INSTANCE, new JobParameters());
        execution.setStatus(BatchStatus.STARTED);
        execution.setStartTime(LocalDateTime.now().minusHours(1));
        var step = new StepExecution(10L, "settleSellerStep", execution);
        step.setStatus(BatchStatus.STARTED);
        step.setLastUpdated(stepLastUpdated);
        execution.addStepExecution(step);
        return execution;
    }

    private void givenCompletedRun(LocalDateTime cutoffAt) throws Exception {
        given(jobRepository.getLastJobExecution(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willReturn(null);
        given(jobRepository.findRunningJobExecutions(SettlementJobConfig.JOB_NAME)).willReturn(Set.of());
        var parameters = new JobParametersBuilder().addLocalDateTime(SettlementJobConfig.CUTOFF_AT, cutoffAt)
            .addString(SettlementJobConfig.TRIGGER_TYPE, "SCHEDULED", false).toJobParameters();
        var completed = new JobExecution(1L, INSTANCE, parameters);
        completed.setStatus(BatchStatus.COMPLETED);
        given(jobOperator.start(eq(settlementJob), eq(parameters))).willReturn(completed);
    }

    private void runScheduled() {
        settlementRunService.runScheduled(LocalDate.of(2026, 11, 15));
    }

    // 실행 전 확인(완료 · 실행 중)은 통과하고 시작에서 실패한다
    private void startFails(Exception failure) throws Exception {
        given(jobRepository.getLastJobExecution(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willReturn(null);
        given(jobRepository.findRunningJobExecutions(SettlementJobConfig.JOB_NAME)).willReturn(Set.of());
        given(jobOperator.start(eq(settlementJob), any(JobParameters.class))).willThrow(failure);
    }

    private void givenLastExecution(BatchStatus status) {
        var execution = new JobExecution(1L, INSTANCE, new JobParameters());
        execution.setStatus(status);
        given(jobRepository.getJobInstance(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willReturn(INSTANCE);
        given(jobRepository.getLastJobExecution(INSTANCE)).willReturn(execution);
    }

    private void assertErrorCode(SettlementErrorCode errorCode) {
        assertThatThrownBy(this::runScheduled).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
