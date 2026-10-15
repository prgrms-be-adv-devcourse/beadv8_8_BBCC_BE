package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
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
}
