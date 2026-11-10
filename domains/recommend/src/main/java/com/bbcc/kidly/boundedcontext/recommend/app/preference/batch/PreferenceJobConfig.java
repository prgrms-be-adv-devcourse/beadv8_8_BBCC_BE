package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceCalculator;
import java.time.LocalDateTime;
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
    private static final String BASE_TIME_EXPRESSION =
        "#{jobExecutionContext['" + PreferenceBaseTimeListener.BASE_TIME + "']}";

    @Bean
    public Job preferenceJob(JobRepository jobRepository, Step childPreferenceStep, Step memberPreferenceStep,
        Step stalePreferenceCleanupStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .listener(new PreferenceBaseTimeListener())
            .start(childPreferenceStep)
            .next(memberPreferenceStep)
            .next(stalePreferenceCleanupStep)
            .build();
    }

    @Bean
    public Step childPreferenceStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
        PreferenceTargetReader childPreferenceReader, PreferenceTargetProcessor childPreferenceProcessor,
        PreferenceTargetWriter childPreferenceWriter,
        @Value("${kidly.recommend.preference.chunk-size:100}") int chunkSize) {
        return new StepBuilder("childPreferenceStep", jobRepository)
            .<Long, PreferenceResult>chunk(chunkSize)
            .transactionManager(transactionManager)
            .reader(childPreferenceReader)
            .processor(childPreferenceProcessor)
            .writer(childPreferenceWriter)
            .build();
    }

    @Bean
    public Step memberPreferenceStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
        PreferenceTargetReader memberPreferenceReader, PreferenceTargetProcessor memberPreferenceProcessor,
        PreferenceTargetWriter memberPreferenceWriter,
        @Value("${kidly.recommend.preference.chunk-size:100}") int chunkSize) {
        return new StepBuilder("memberPreferenceStep", jobRepository)
            .<Long, PreferenceResult>chunk(chunkSize)
            .transactionManager(transactionManager)
            .reader(memberPreferenceReader)
            .processor(memberPreferenceProcessor)
            .writer(memberPreferenceWriter)
            .build();
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
    public PreferenceTargetReader childPreferenceReader(PreferenceSourceReader sourceReader,
        PreferenceSnapshotStore snapshotStore, @Value(BASE_TIME_EXPRESSION) LocalDateTime baseTime,
        @Value("${kidly.recommend.preference.chunk-size:100}") int chunkSize) {
        return new PreferenceTargetReader(new ChildPreferenceTargets(sourceReader, snapshotStore), baseTime, chunkSize);
    }

    @Bean
    @StepScope
    public PreferenceTargetProcessor childPreferenceProcessor(PreferenceSourceReader sourceReader,
        PreferenceSnapshotStore snapshotStore, @Value(BASE_TIME_EXPRESSION) LocalDateTime baseTime) {
        return new PreferenceTargetProcessor(new ChildPreferenceTargets(sourceReader, snapshotStore), sourceReader,
            new PreferenceCalculator(), baseTime);
    }

    @Bean
    public PreferenceTargetWriter childPreferenceWriter(PreferenceSourceReader sourceReader,
        PreferenceSnapshotStore snapshotStore) {
        return new PreferenceTargetWriter(new ChildPreferenceTargets(sourceReader, snapshotStore));
    }

    @Bean
    @StepScope
    public PreferenceTargetReader memberPreferenceReader(PreferenceSourceReader sourceReader,
        PreferenceSnapshotStore snapshotStore, @Value(BASE_TIME_EXPRESSION) LocalDateTime baseTime,
        @Value("${kidly.recommend.preference.chunk-size:100}") int chunkSize) {
        return new PreferenceTargetReader(new MemberPreferenceTargets(sourceReader, snapshotStore), baseTime, chunkSize);
    }

    @Bean
    @StepScope
    public PreferenceTargetProcessor memberPreferenceProcessor(PreferenceSourceReader sourceReader,
        PreferenceSnapshotStore snapshotStore, @Value(BASE_TIME_EXPRESSION) LocalDateTime baseTime) {
        return new PreferenceTargetProcessor(new MemberPreferenceTargets(sourceReader, snapshotStore), sourceReader,
            new PreferenceCalculator(), baseTime);
    }

    @Bean
    public PreferenceTargetWriter memberPreferenceWriter(PreferenceSourceReader sourceReader,
        PreferenceSnapshotStore snapshotStore) {
        return new PreferenceTargetWriter(new MemberPreferenceTargets(sourceReader, snapshotStore));
    }

    @Bean
    @StepScope
    public StalePreferenceCleanupTasklet stalePreferenceCleanupTasklet(PreferenceSnapshotStore snapshotStore,
        @Value(BASE_TIME_EXPRESSION) LocalDateTime baseTime) {
        return new StalePreferenceCleanupTasklet(snapshotStore, baseTime);
    }
}
