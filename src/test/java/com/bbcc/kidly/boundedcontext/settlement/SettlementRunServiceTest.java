package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
import com.bbcc.kidly.boundedcontext.settlement.app.support.SettlementRunRecorder;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRunRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementTriggerType;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private static final Long RUN_ID = 7L;

    @Mock
    private SettlementRunRepository settlementRunRepository;

    @Mock
    private SettlementRunRecorder settlementRunRecorder;

    @Mock
    private JobOperator jobOperator;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private Job settlementJob;

    @InjectMocks
    private SettlementRunService settlementRunService;

    @Test
    @DisplayName("작업을 시작하다 예상 못 한 예외가 나면 실행 기록을 실패로 마감하고 예외를 그대로 던진다")
    void startFailure() throws Exception {
        var cutoffAt = LocalDateTime.of(2026, 11, 1, 0, 0);
        given(jobRepository.getLastJobExecution(eq(SettlementJobConfig.JOB_NAME), any(JobParameters.class)))
            .willReturn(null);
        given(settlementRunRecorder.start(cutoffAt, SettlementTriggerType.SCHEDULED, null)).willReturn(RUN_ID);
        var failure = new DataAccessResourceFailureException("DB 연결 실패");
        given(jobOperator.start(eq(settlementJob), any(JobParameters.class))).willThrow(failure);

        assertThatThrownBy(() -> settlementRunService.runScheduled(LocalDate.of(2026, 11, 15))).isSameAs(failure);
        verify(settlementRunRecorder).fail(RUN_ID, 0, 0);
    }
}
