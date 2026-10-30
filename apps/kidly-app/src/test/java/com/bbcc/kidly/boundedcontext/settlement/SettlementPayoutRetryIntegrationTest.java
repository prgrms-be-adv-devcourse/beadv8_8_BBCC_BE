package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.app.AdminSettlementService;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementPayoutRetryScheduler;
import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 관리자 지급 재요청이 결제의 실제 리스너를 거쳐 정산서에 반영되는지 확인한다.
 * 정산서를 바로 저장해 지급 결과를 받지 못한(COMPLETED) 상태를 만든다.
 */
@IntegrationTest
class SettlementPayoutRetryIntegrationTest {

    private static final long TIMEOUT_SECONDS = 60;

    @Autowired
    private AdminSettlementService adminSettlementService;

    @Autowired
    private SettlementPayoutRetryScheduler retryScheduler;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("재요청하면 결제가 입금하고 정산서가 지급 완료가 된다. 다시 재요청하면 SETTLEMENT_NOT_RETRYABLE이다")
    void retry() {
        var settlement = saveCompleted();

        adminSettlementService.retryPayout(settlement.getId());

        var reloaded = reload(settlement);
        assertThat(reloaded.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(walletBalance()).isEqualTo(settlement.getSettlementAmount());
        assertThatThrownBy(() -> adminSettlementService.retryPayout(settlement.getId()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.SETTLEMENT_NOT_RETRYABLE));
        assertThat(payoutCount(settlement)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 정산서를 동시에 두 번 재요청해도 입금은 1번, 정산서 상태 변경도 1번이다")
    void retryTwiceAtOnce() throws Exception {
        var settlement = saveCompleted();
        var versionBefore = settlement.getVersion();

        // 늦게 시작한 쪽은 이미 지급 완료를 보고 409로 끝날 수 있다
        var errors = runTwiceAtOnce(() -> adminSettlementService.retryPayout(settlement.getId()));

        assertThat(errors).allSatisfy(error -> assertThat(error).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.SETTLEMENT_NOT_RETRYABLE)));
        var reloaded = reload(settlement);
        assertThat(reloaded.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(reloaded.getVersion()).isEqualTo(versionBefore + 1);
        assertThat(walletBalance()).isEqualTo(settlement.getSettlementAmount());
        assertThat(payoutCount(settlement)).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 정산서를 재요청하면 NOT_FOUND다")
    void retryNotFound() {
        assertThatThrownBy(() -> adminSettlementService.retryPayout(999L))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("30분 넘게 결과를 못 받은 정산서는 자동 재요청으로 지급 완료가 된다")
    void retriesStuck() {
        var settlement = saveCompleted();

        var retried = retryScheduler.retryStuckPayouts(SettlementFixture.NOW.plusHours(1));

        assertThat(retried).isEqualTo(1);
        assertThat(reload(settlement).getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(walletBalance()).isEqualTo(settlement.getSettlementAmount());
        assertThat(payoutCount(settlement)).isEqualTo(1);
    }

    @Test
    @DisplayName("30분이 안 됐거나 7일이 넘은 정산서는 건드리지 않는다")
    void ignoresRecentAndOld() {
        var settlement = saveCompleted();

        assertThat(retryScheduler.retryStuckPayouts(SettlementFixture.NOW.plusMinutes(10))).isZero();
        assertThat(retryScheduler.retryStuckPayouts(SettlementFixture.NOW.plusDays(8))).isZero();

        assertThat(reload(settlement).getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(payoutCount(settlement)).isZero();
    }

    @Test
    @DisplayName("자동 재요청을 두 번 돌려도 입금은 한 번이다")
    void retryTwiceDoesNotPayTwice() {
        var settlement = saveCompleted();
        var now = SettlementFixture.NOW.plusHours(1);

        retryScheduler.retryStuckPayouts(now);
        var second = retryScheduler.retryStuckPayouts(now);

        assertThat(second).isZero();
        assertThat(payoutCount(settlement)).isEqualTo(1);
    }

    private Settlement saveCompleted() {
        return settlementRepository.save(SettlementFixture.settlement(10_000L));
    }

    private Settlement reload(Settlement settlement) {
        return settlementRepository.findById(settlement.getId()).orElseThrow();
    }

    private Long walletBalance() {
        return jdbcTemplate.queryForObject(
            "SELECT balance FROM payment.wallet WHERE member_id = ?", Long.class, SettlementFixture.SELLER_ID);
    }

    private Integer payoutCount(Settlement settlement) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM payment.payout WHERE settlement_id = ?", Integer.class, settlement.getId());
    }

    // 두 작업을 출발 신호 하나로 함께 시작하고, 실패한 작업의 예외를 돌려준다
    private static List<Throwable> runTwiceAtOnce(Runnable task) throws InterruptedException {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    task.run();
                    return null;
                }));
            }
            start.countDown();
            var errors = new ArrayList<Throwable>();
            for (var future : futures) {
                try {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    errors.add(e.getCause());
                } catch (TimeoutException e) {
                    errors.add(e);
                }
            }
            return errors;
        } finally {
            executor.shutdownNow();
        }
    }
}
