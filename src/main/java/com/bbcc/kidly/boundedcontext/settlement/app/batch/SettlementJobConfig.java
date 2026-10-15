package com.bbcc.kidly.boundedcontext.settlement.app.batch;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementProperties;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSellerQuery;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRunRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 판매자별 정산 작업. 판매자 ID를 묶음으로 읽어 한 트랜잭션에서 정산하고, 묶음이 실패하면 판매자 1명씩 다시 처리해
 * 정산할 수 없는 판매자만 건너뛴다. 그 밖의 예외는 작업을 실패시킨다.
 */
@Configuration
public class SettlementJobConfig {

    public static final String JOB_NAME = "settlementJob";
    // 같은 기준일은 같은 작업 인스턴스다
    public static final String CUTOFF_AT = "cutoffAt";
    // 실행 기록 ID. 작업 인스턴스를 구분하는 데 쓰지 않는다
    public static final String RUN_ID = "runId";
    private static final String STEP_NAME = "settleSellerStep";

    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settleSellerStep, SettlementRunListener listener) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .listener(listener)
            .start(settleSellerStep)
            .build();
    }

    @Bean
    public Step settleSellerStep(
        JobRepository jobRepository, PlatformTransactionManager transactionManager, SettlementProperties properties,
        SettlementSellerReader settlementSellerReader, SellerSettlementWriter sellerSettlementWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
            .<Long, Long>chunk(properties.chunkSize())
            .transactionManager(transactionManager)
            .reader(settlementSellerReader)
            .writer(sellerSettlementWriter)
            .faultTolerant()
            .skip(IllegalArgumentException.class, IllegalStateException.class, ArithmeticException.class,
                OptimisticLockingFailureException.class, DataIntegrityViolationException.class)
            .skipLimit(Long.MAX_VALUE)
            .skipListener(new SellerSettlementSkipListener())
            .build();
    }

    @Bean
    @StepScope
    public SettlementSellerReader settlementSellerReader(
        SettlementSellerQuery settlementSellerQuery, SettlementProperties properties,
        @Value("#{jobParameters['" + CUTOFF_AT + "']}") LocalDateTime cutoffAt) {
        return new SettlementSellerReader(settlementSellerQuery, cutoffAt, properties.chunkSize());
    }

    @Bean
    @StepScope
    public SellerSettlementWriter sellerSettlementWriter(
        SettlementRunRepository settlementRunRepository, SettlementItemRepository settlementItemRepository,
        SettlementRepository settlementRepository, SettlementProperties properties,
        @Value("#{jobParameters['" + RUN_ID + "']}") Long runId) {
        return new SellerSettlementWriter(
            settlementRunRepository, settlementItemRepository, settlementRepository, properties.feeRate(), runId);
    }
}
