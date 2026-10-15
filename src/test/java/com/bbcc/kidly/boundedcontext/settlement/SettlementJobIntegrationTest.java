package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.FEE_RATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.global.error.ErrorCode;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 수동 실행은 미래 기준일을 거부하므로 기준일은 이번 달 1일(오늘 또는 지난 날)로 잡는다
@IntegrationTest
class SettlementJobIntegrationTest {

    private static final Long ADMIN_ID = 3L;
    private static final LocalDate CUTOFF_ON = LocalDate.now().withDayOfMonth(1);
    private static final LocalDateTime CUTOFF_AT = CUTOFF_ON.atStartOfDay();
    private static final LocalDate PERIOD_START = CUTOFF_ON.minusMonths(1);
    private static final LocalDate PERIOD_END = CUTOFF_ON.minusDays(1);

    @Autowired
    private SettlementRunService settlementRunService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private SettlementItemRepository settlementItemRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("기준일 전에 확정된 대기 항목을 판매자별 정산서로 만들고 항목을 정산 처리한다")
    void settleBySeller() {
        var first = save(201L, 10L, 10_000L, PERIOD_START.atStartOfDay());
        var second = save(202L, 10L, 5_009L, CUTOFF_AT.minusMinutes(1));
        var other = save(203L, 11L, 20_000L, CUTOFF_AT.minusDays(3));
        var excluded = save(204L, 12L, 10_000L, CUTOFF_AT);

        var result = settlementRunService.runManually(CUTOFF_ON, ADMIN_ID);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failCount()).isZero();
        assertThat(result.periodStartOn()).isEqualTo(PERIOD_START);
        assertThat(result.periodEndOn()).isEqualTo(PERIOD_END);
        assertThat(result.triggerType()).isEqualTo("MANUAL");
        assertThat(result.requestedBy()).isEqualTo(ADMIN_ID);
        assertThat(result.finishedAt()).isNotNull();

        var sellerSettlement = settlementOf(first);
        assertThat(sellerSettlement.getSellerId()).isEqualTo(10L);
        assertThat(sellerSettlement.getTotalSalesAmount()).isEqualTo(15_009L);
        assertThat(sellerSettlement.getFeeAmount()).isEqualTo(1_500L);
        assertThat(sellerSettlement.getSettlementAmount()).isEqualTo(13_509L);
        assertThat(sellerSettlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(sellerSettlement.getPeriodStartOn()).isEqualTo(PERIOD_START);
        assertThat(sellerSettlement.getPeriodEndOn()).isEqualTo(PERIOD_END);
        assertThat(reload(second).getSettlementId()).isEqualTo(sellerSettlement.getId());

        var otherSettlement = settlementOf(other);
        assertThat(otherSettlement.getSellerId()).isEqualTo(11L);
        assertThat(otherSettlement.getTotalSalesAmount()).isEqualTo(20_000L);
        assertThat(otherSettlement.getFeeAmount()).isEqualTo(2_000L);
        assertThat(otherSettlement.getSettlementAmount()).isEqualTo(18_000L);

        assertThat(reload(first).getStatus()).isEqualTo(SettlementItemStatus.SETTLED);
        assertThat(reload(other).getStatus()).isEqualTo(SettlementItemStatus.SETTLED);
        assertThat(reload(excluded).getStatus()).isEqualTo(SettlementItemStatus.PENDING);
        assertThat(settlementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("정산할 항목이 없으면 정산서 없이 완료된다")
    void noTargets() {
        var result = settlementRunService.runManually(CUTOFF_ON, ADMIN_ID);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successCount()).isZero();
        assertThat(result.failCount()).isZero();
        assertThat(settlementCount()).isZero();
    }

    @Test
    @DisplayName("이미 완료한 기준일로 다시 실행하면 SETTLEMENT_ALREADY_RUN이고 실행 기록을 남기지 않는다")
    void alreadyRun() {
        save(201L, 10L, 10_000L, CUTOFF_AT.minusDays(1));
        settlementRunService.runManually(CUTOFF_ON, ADMIN_ID);

        assertErrorCode(() -> settlementRunService.runManually(CUTOFF_ON, ADMIN_ID),
            SettlementErrorCode.SETTLEMENT_ALREADY_RUN);
        assertThat(settlementCount()).isEqualTo(1);
        assertThat(runCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 기준일 정산이 실행 중이면 SETTLEMENT_RUN_IN_PROGRESS이고 작업을 실행하지 않는다")
    void runInProgress() {
        startExecution(CUTOFF_AT.minusMonths(1));

        assertErrorCode(() -> settlementRunService.runManually(CUTOFF_ON, ADMIN_ID),
            SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS);
        assertThat(runCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("계산에 실패한 판매자만 건너뛰고 나머지는 정산해 PARTIAL_FAILED다")
    void partialFailed() {
        var overflow = save(201L, 10L, Long.MAX_VALUE, CUTOFF_AT.minusDays(2));
        save(202L, 10L, Long.MAX_VALUE, CUTOFF_AT.minusDays(1));
        var other = save(203L, 11L, 1_000L, CUTOFF_AT.minusDays(1));

        var result = settlementRunService.runManually(CUTOFF_ON, ADMIN_ID);

        assertThat(result.status()).isEqualTo("PARTIAL_FAILED");
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failCount()).isEqualTo(1);
        assertThat(settlementCount()).isEqualTo(1);
        assertThat(settlementOf(other).getSellerId()).isEqualTo(11L);
        assertThat(reload(overflow).getStatus()).isEqualTo(SettlementItemStatus.PENDING);
    }

    @Test
    @DisplayName("저장에 실패한 판매자만 건너뛰고 같은 묶음의 나머지 판매자는 정산한다")
    void writeFailureSkipsOnlyThatSeller() {
        settlementRepository.save(Settlement.create(
            new SettlementCreateInfo(10L, PERIOD_START, PERIOD_END, 1_000L, FEE_RATE), LocalDateTime.now()));
        var conflicted = save(201L, 10L, 10_000L, CUTOFF_AT.minusDays(1));
        var other = save(202L, 11L, 1_000L, CUTOFF_AT.minusDays(1));

        var result = settlementRunService.runManually(CUTOFF_ON, ADMIN_ID);

        assertThat(result.status()).isEqualTo("PARTIAL_FAILED");
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failCount()).isEqualTo(1);
        assertThat(reload(conflicted).getStatus()).isEqualTo(SettlementItemStatus.PENDING);
        assertThat(settlementOf(other).getTotalSalesAmount()).isEqualTo(1_000L);
        assertThat(settlementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("기준일에 확정돼 남은 항목은 다음 기준일 실행에서 그 기간 정산서로 만든다")
    void nextCutoffSettlesLeftovers() {
        var previousCutoffOn = CUTOFF_ON.minusMonths(1);
        save(201L, 10L, 10_000L, previousCutoffOn.atStartOfDay().minusDays(1));
        var leftover = save(202L, 12L, 10_000L, previousCutoffOn.atStartOfDay());
        settlementRunService.runManually(previousCutoffOn, ADMIN_ID);
        assertThat(reload(leftover).getStatus()).isEqualTo(SettlementItemStatus.PENDING);

        var result = settlementRunService.runManually(CUTOFF_ON, ADMIN_ID);

        assertThat(result.successCount()).isEqualTo(1);
        var settlement = settlementOf(leftover);
        assertThat(settlement.getSellerId()).isEqualTo(12L);
        assertThat(settlement.getPeriodStartOn()).isEqualTo(PERIOD_START);
        assertThat(settlement.getPeriodEndOn()).isEqualTo(PERIOD_END);
        assertThat(settlementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("미래 기준일로 수동 실행하면 INVALID_SETTLEMENT_CUTOFF이고 실행 기록을 남기지 않는다")
    void futureCutoff() {
        assertErrorCode(() -> settlementRunService.runManually(LocalDate.now().plusDays(1), ADMIN_ID),
            SettlementErrorCode.INVALID_SETTLEMENT_CUTOFF);
        assertThat(runCount()).isZero();
    }

    @Test
    @DisplayName("자동 실행은 그 달 1일 0시를 기준으로 관리자 없이 실행한다")
    void scheduled() {
        var result = settlementRunService.runScheduled(LocalDate.of(2026, 11, 15));

        assertThat(result.cutoffAt()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(result.triggerType()).isEqualTo("SCHEDULED");
        assertThat(result.requestedBy()).isNull();
        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("없는 실행을 조회하면 NOT_FOUND다")
    void getRunNotFound() {
        assertErrorCode(() -> settlementRunService.getRun(999L), CommonErrorCode.NOT_FOUND);
    }

    private SettlementItem save(Long orderItemId, Long sellerId, long salesAmount, LocalDateTime confirmedAt) {
        return settlementItemRepository.save(SettlementFixture.item(orderItemId, sellerId, salesAmount, confirmedAt));
    }

    private SettlementItem reload(SettlementItem item) {
        return settlementItemRepository.findById(item.getId()).orElseThrow();
    }

    private Settlement settlementOf(SettlementItem item) {
        return settlementRepository.findById(reload(item).getSettlementId()).orElseThrow();
    }

    private Integer settlementCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM settlement.settlement", Integer.class);
    }

    private Integer runCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM settlement.BATCH_JOB_EXECUTION", Integer.class);
    }

    private void startExecution(LocalDateTime cutoffAt) {
        var parameters = new JobParametersBuilder().addLocalDateTime(SettlementJobConfig.CUTOFF_AT, cutoffAt)
            .toJobParameters();
        var instance = jobRepository.createJobInstance(SettlementJobConfig.JOB_NAME, parameters);
        var execution = jobRepository.createJobExecution(instance, parameters, new ExecutionContext());
        execution.setStatus(BatchStatus.STARTED);
        execution.setStartTime(LocalDateTime.now());
        jobRepository.update(execution);
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
