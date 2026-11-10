package com.bbcc.kidly.boundedcontext.recommend.app.preference;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceJobRunLock;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

/**
 * 선호 Job을 기준일로 실행한다.
 */
public class PreferenceJobService {

    public PreferenceJobService(JobOperator jobOperator, JobRepository jobRepository, Job preferenceJob,
        PreferenceJobRunLock runLock, Duration staleAfter) {
    }

    public PreferenceJobResult run(LocalDate baseDate) {
        return PreferenceJobResult.COMPLETED;
    }
}
