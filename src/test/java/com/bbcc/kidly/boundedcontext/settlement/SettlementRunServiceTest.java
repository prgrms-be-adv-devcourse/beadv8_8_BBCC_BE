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
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class SettlementRunServiceTest {

    @Mock
    private JobOperator jobOperator;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private Job settlementJob;

    @InjectMocks
    private SettlementRunService settlementRunService;

    @Test
    @DisplayName("작업을 시작하다 예상 못 한 예외가 나면 그대로 던진다")
    void startFailure() throws Exception {
        given(jobRepository.getLastJobExecution(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willReturn(null);
        given(jobRepository.findRunningJobExecutions(SettlementJobConfig.JOB_NAME)).willReturn(Set.of());
        var failure = new DataAccessResourceFailureException("DB 연결 실패");
        given(jobOperator.start(eq(settlementJob), any(JobParameters.class))).willThrow(failure);

        assertThatThrownBy(() -> settlementRunService.runScheduled(LocalDate.of(2026, 11, 15))).isSameAs(failure);
    }

    @Test
    @DisplayName("같은 기준일을 동시에 시작해 작업 인스턴스가 겹치면 SETTLEMENT_RUN_IN_PROGRESS다")
    void concurrentStart() throws Exception {
        given(jobRepository.getLastJobExecution(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willReturn(null);
        given(jobRepository.findRunningJobExecutions(SettlementJobConfig.JOB_NAME)).willReturn(Set.of());
        given(jobOperator.start(eq(settlementJob), any(JobParameters.class)))
            .willThrow(new DuplicateKeyException("uk_job_inst"));

        assertThatThrownBy(() -> settlementRunService.runScheduled(LocalDate.of(2026, 11, 15)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS));
    }
}
