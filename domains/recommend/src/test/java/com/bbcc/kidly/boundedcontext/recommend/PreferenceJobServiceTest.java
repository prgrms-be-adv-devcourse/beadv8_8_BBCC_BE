package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceJobRunLock;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.PreferenceJobService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
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
}
