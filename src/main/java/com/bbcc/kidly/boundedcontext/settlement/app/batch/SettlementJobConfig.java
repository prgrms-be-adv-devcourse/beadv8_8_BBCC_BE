package com.bbcc.kidly.boundedcontext.settlement.app.batch;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementProperties;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSellerQuery;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
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
    // 실행 방식 · 수동 실행한 관리자 ID. 작업 인스턴스를 구분하는 데 쓰지 않고, 자동 실행이면 관리자 ID가 없다
    public static final String TRIGGER_TYPE = "triggerType";
    public static final String REQUESTED_BY = "requestedBy";
    private static final String STEP_NAME = "settleSellerStep";

    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settleSellerStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
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
        SettlementItemRepository settlementItemRepository, SettlementRepository settlementRepository,
        SettlementProperties properties, @Value("#{jobParameters['" + CUTOFF_AT + "']}") LocalDateTime cutoffAt) {
        return new SellerSettlementWriter(
            settlementItemRepository, settlementRepository, properties.feeRate(), cutoffAt);
    }
}
