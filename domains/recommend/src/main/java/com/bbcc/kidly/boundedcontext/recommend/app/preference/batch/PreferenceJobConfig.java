package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceCalculator;
import java.time.LocalDateTime;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 자녀 · 회원 선호를 매일 다시 계산하는 Job이다. 자녀 Step, 회원 Step, 지난 줄 정리 Step 순서로 돈다.
 * 대상 하나라도 실패하면 Step이 실패하고, 같은 기준일로 다시 실행하면 실패한 chunk부터 이어서 한다.
 * 대상 ID를 오름차순으로 한 스레드에서 읽어 chunk끼리 겹치지 않는다.
 */
@Configuration
public class PreferenceJobConfig {

    public static final String JOB_NAME = "preferenceJob";
    // 같은 기준일(KST 날짜)은 같은 Job 인스턴스다
    public static final String BASE_DATE = "baseDate";
    // 기준일의 실행이 함께 쓰는 배치 기준 시각이다. 같은 인스턴스를 가르지 않도록 identifying이 아닌 파라미터로 넘긴다
    public static final String BASE_TIME = "baseTime";
    private static final String BASE_TIME_EXPRESSION = "#{jobParameters['" + BASE_TIME + "']}";

    private final int chunkSize;

    public PreferenceJobConfig(@Value("${kidly.recommend.preference.chunk-size:100}") int chunkSize) {
        this.chunkSize = chunkSize;
    }

    @Bean
    public Job preferenceJob(JobRepository jobRepository, Step childPreferenceStep, Step memberPreferenceStep,
        Step stalePreferenceCleanupStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .start(childPreferenceStep)
            .next(memberPreferenceStep)
            .next(stalePreferenceCleanupStep)
            .build();
    }

    // Reader · Processor가 실행마다 다른 배치 기준 시각을 받으므로 대상 Step을 Job 실행마다 만든다
    @Bean
    @JobScope
    public Step childPreferenceStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
        PreferenceSourceReader sourceReader, PreferenceSnapshotStore snapshotStore,
        @Value(BASE_TIME_EXPRESSION) LocalDateTime baseTime) {
        return preferenceStep(new StepBuilder("childPreferenceStep", jobRepository), transactionManager,
            new ChildPreferenceTargets(sourceReader, snapshotStore), sourceReader, baseTime);
    }

    @Bean
    @JobScope
    public Step memberPreferenceStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
        PreferenceSourceReader sourceReader, PreferenceSnapshotStore snapshotStore,
        @Value(BASE_TIME_EXPRESSION) LocalDateTime baseTime) {
        return preferenceStep(new StepBuilder("memberPreferenceStep", jobRepository), transactionManager,
            new MemberPreferenceTargets(sourceReader, snapshotStore), sourceReader, baseTime);
    }

    @Bean
    public Step stalePreferenceCleanupStep(JobRepository jobRepository,
        PlatformTransactionManager transactionManager, StalePreferenceCleanupTasklet stalePreferenceCleanupTasklet) {
        return new StepBuilder("stalePreferenceCleanupStep", jobRepository)
            .tasklet(stalePreferenceCleanupTasklet, transactionManager)
            .build();
    }

    @Bean
    @StepScope
    public StalePreferenceCleanupTasklet stalePreferenceCleanupTasklet(PreferenceSnapshotStore snapshotStore,
        @Value(BASE_TIME_EXPRESSION) LocalDateTime baseTime) {
        return new StalePreferenceCleanupTasklet(snapshotStore, baseTime);
    }

    private Step preferenceStep(StepBuilder builder, PlatformTransactionManager transactionManager,
        PreferenceTargets targets, PreferenceSourceReader sourceReader, LocalDateTime baseTime) {
        return builder
            .<Long, PreferenceResult>chunk(chunkSize)
            .transactionManager(transactionManager)
            .reader(new PreferenceTargetReader(targets, baseTime, chunkSize))
            .processor(new PreferenceTargetProcessor(targets, sourceReader, new PreferenceCalculator(), baseTime))
            .writer(new PreferenceTargetWriter(targets))
            .build();
    }
}
