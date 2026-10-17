package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class SettlementRunServiceTest {

    private static final JobInstance INSTANCE = new JobInstance(1L, SettlementJobConfig.JOB_NAME);

    @Mock
    private JobOperator jobOperator;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private Job settlementJob;

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
