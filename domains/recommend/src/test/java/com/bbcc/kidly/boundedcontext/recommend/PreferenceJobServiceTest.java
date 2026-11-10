package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceJobRunLock;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.PreferenceJobResult;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.PreferenceJobService;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.PreferenceJobConfig;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

@ExtendWith(MockitoExtension.class)
class PreferenceJobServiceTest {

    @Mock
    private JobOperator jobOperator;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private Job preferenceJob;

    @Mock
    private PreferenceJobRunLock runLock;

    private PreferenceJobService service;

    @BeforeEach
    void setUp() {
        service = new PreferenceJobService(jobOperator, jobRepository, preferenceJob, runLock, Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("PREF-11 오늘(KST)이 아닌 기준일은 옛 계산으로 새 줄을 덮지 않도록 실행하지 않고 거절한다")
    void rejectBaseDateOtherThanToday() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);

        assertThatThrownBy(() -> service.run(yesterday)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(runLock, jobOperator);
    }

    @Test
    @DisplayName("PREF-11 다른 서버가 잠금을 쥐고 있으면 Job을 시작하지 않고 LOCKED다")
    void lockedWhenAnotherServerHoldsLock() {
        given(runLock.runExclusively(any())).willReturn(false);

        assertThat(service.run(LocalDate.now(ZoneId.of("Asia/Seoul")))).isEqualTo(PreferenceJobResult.LOCKED);
        verifyNoInteractions(jobOperator, jobRepository);
    }

    @Test
    @DisplayName("PREF-11 같은 기준일의 앞 실행이 있으면 그 실행의 배치 기준 시각을 다시 실행에 넘긴다")
    void reuseBaseTimeOfLastExecution() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDateTime firstBaseTime = today.atTime(3, 0, 12);
        JobExecution last = execution(new JobParametersBuilder()
            .addLocalDate(PreferenceJobConfig.BASE_DATE, today)
            .addLocalDateTime(PreferenceJobConfig.BASE_TIME, firstBaseTime, false)
            .toJobParameters(), BatchStatus.FAILED);
        given(runLock.runExclusively(any())).willAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return true;
        });
        given(jobRepository.getLastJobExecution(eq(PreferenceJobConfig.JOB_NAME), any())).willReturn(last);
        given(jobOperator.start(eq(preferenceJob), any()))
            .willReturn(execution(new JobParameters(), BatchStatus.COMPLETED));

        assertThat(service.run(today)).isEqualTo(PreferenceJobResult.COMPLETED);

        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(preferenceJob), parameters.capture());
        assertThat(parameters.getValue().getLocalDateTime(PreferenceJobConfig.BASE_TIME)).isEqualTo(firstBaseTime);
        assertThat(parameters.getValue().getLocalDate(PreferenceJobConfig.BASE_DATE)).isEqualTo(today);
    }

    private static JobExecution execution(JobParameters parameters, BatchStatus status) {
        JobExecution execution = new JobExecution(1L, new JobInstance(1L, PreferenceJobConfig.JOB_NAME), parameters);
        execution.setStatus(status);
        return execution;
    }
}
