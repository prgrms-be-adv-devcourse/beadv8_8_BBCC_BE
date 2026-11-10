package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.PreferenceBaseTimeListener;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;

class PreferenceBaseTimeListenerTest {

    private final PreferenceBaseTimeListener listener = new PreferenceBaseTimeListener();

    @Test
    @DisplayName("PREF-01 첫 실행은 Job을 시작한 한국 시각을 배치 기준 시각으로 남긴다")
    void putStartTimeOnFirstRun() {
        JobExecution execution = newExecution();
        LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        listener.beforeJob(execution);

        Object baseTime = execution.getExecutionContext().get(PreferenceBaseTimeListener.BASE_TIME);
        assertThat(baseTime).isInstanceOfSatisfying(LocalDateTime.class, time -> assertThat(time)
            .isBetween(before, LocalDateTime.now(ZoneId.of("Asia/Seoul"))));
    }

    @Test
    @DisplayName("PREF-11 다시 실행해 앞 실행의 배치 기준 시각이 이미 있으면 바꾸지 않는다")
    void keepBaseTimeOnRestart() {
        JobExecution execution = newExecution();
        LocalDateTime firstRun = LocalDateTime.of(2026, 11, 10, 3, 0, 12);
        execution.getExecutionContext().put(PreferenceBaseTimeListener.BASE_TIME, firstRun);

        listener.beforeJob(execution);

        assertThat(execution.getExecutionContext().get(PreferenceBaseTimeListener.BASE_TIME)).isEqualTo(firstRun);
    }

    private static JobExecution newExecution() {
        return new JobExecution(1L, new JobInstance(1L, "preferenceJob"), new JobParameters());
    }
}
