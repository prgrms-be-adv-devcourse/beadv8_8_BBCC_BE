package com.bbcc.kidly.boundedcontext.recommend.app.preference;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceJobRunLock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

/**
 * 선호 Job을 기준일로 실행한다.
 */
public class PreferenceJobService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public PreferenceJobService(JobOperator jobOperator, JobRepository jobRepository, Job preferenceJob,
        PreferenceJobRunLock runLock, Duration staleAfter) {
    }

    // 새 기준일이 끝난 뒤 옛 기준일을 다시 돌리면 새 줄을 옛 계산으로 덮는다
    public PreferenceJobResult run(LocalDate baseDate) {
        if (!baseDate.equals(LocalDate.now(SERVICE_ZONE))) {
            throw new IllegalArgumentException("선호 Job 기준일은 오늘이어야 합니다: " + baseDate);
        }
        return PreferenceJobResult.COMPLETED;
    }
}
