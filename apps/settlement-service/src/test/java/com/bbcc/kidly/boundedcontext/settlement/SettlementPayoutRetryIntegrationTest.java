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
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 지급 재요청 · 자동 재요청이 지급 요청을 다시 보내고, 지급 결과가 정산서에 반영되는지 확인한다.
 * 정산서를 바로 저장해 지급 결과를 받지 못한(COMPLETED) 상태를 만든다. 입금은 결제 서비스가 하므로(#525)
 * 지급 요청은 정산 아웃박스로 보고, 결과는 테스트가 결제 서비스 대신 발행한다(PayoutRequests).
 */
@IntegrationTest
class SettlementPayoutRetryIntegrationTest {

    private static final Long ADMIN_ID = 7L;

    private static final long TIMEOUT_SECONDS = 60;

    @Autowired
    private AdminSettlementService adminSettlementService;

    @Autowired
    private SettlementPayoutRetryScheduler retryScheduler;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("재요청하면 정산금액으로 지급을 다시 요청하고 관리자 행위를 남기며, 지급 완료된 뒤 다시 재요청하면 "
        + "SETTLEMENT_NOT_RETRYABLE이고 행위도 남지 않는다")
    void retry() {
        var settlement = saveCompleted();

        adminSettlementService.retryPayout(settlement.getId(), ADMIN_ID);

        var requests = PayoutRequests.of(jdbcTemplate, settlement.getId());
        assertThat(requests).singleElement().extracting(PayoutRequests.Request::amount)
            .isEqualTo(settlement.getSettlementAmount());
        PayoutRequests.complete(eventPublisher, transactionTemplate, requests.getFirst());
        assertThat(reload(settlement).getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThatThrownBy(() -> adminSettlementService.retryPayout(settlement.getId(), ADMIN_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.SETTLEMENT_NOT_RETRYABLE));
        assertThat(PayoutRequests.of(jdbcTemplate, settlement.getId())).hasSize(1);
        assertThat(AdminActions.of(jdbcTemplate, "PAYOUT_RETRY", settlement.getId()))
            .containsExactly(new AdminActions.Row(ADMIN_ID, "PAYOUT_RETRY", settlement.getId()));
    }

    @Test
    @DisplayName("같은 정산서를 동시에 두 번 재요청하면 같은 금액으로 지급을 요청하고, 지급 결과는 한 번만 반영한다")
    void retryTwiceAtOnce() throws Exception {
        var settlement = saveCompleted();

        // 재요청은 정산서를 바꾸지 않고 지급 요청만 다시 보낸다. 중복 입금은 결제가 정산서마다 한 번만 입금해 막는다(결제 서비스 테스트)
        var errors = runTwiceAtOnce(() -> adminSettlementService.retryPayout(settlement.getId(), ADMIN_ID));

        assertThat(errors).isEmpty();
        var requests = PayoutRequests.of(jdbcTemplate, settlement.getId());
        assertThat(requests).hasSize(2).allSatisfy(request ->
            assertThat(request.amount()).isEqualTo(settlement.getSettlementAmount()));
        // 결제는 같은 정산서의 지급 요청에 같은 결과를 다시 보낸다
        requests.forEach(request -> PayoutRequests.complete(eventPublisher, transactionTemplate, request));
        var reloaded = reload(settlement);
        assertThat(reloaded.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(reloaded.getVersion()).isEqualTo(settlement.getVersion() + 1);
    }

    @Test
    @DisplayName("없는 정산서를 재요청하면 NOT_FOUND다")
    void retryNotFound() {
        assertThatThrownBy(() -> adminSettlementService.retryPayout(999L, ADMIN_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("30분 넘게 결과를 못 받은 정산서는 자동으로 지급을 다시 요청하고, 결과를 받으면 지급 완료가 된다")
    void retriesStuck() {
        var settlement = saveCompleted();

        var retried = retryScheduler.retryStuckPayouts(SettlementFixture.NOW.plusHours(1));

        assertThat(retried).isEqualTo(1);
        var requests = PayoutRequests.of(jdbcTemplate, settlement.getId());
        assertThat(requests).hasSize(1);
        PayoutRequests.complete(eventPublisher, transactionTemplate, requests.getFirst());
        assertThat(reload(settlement).getStatus()).isEqualTo(SettlementStatus.PAID);
        // 자동 재요청은 관리자 행위가 아니다
        assertThat(AdminActions.of(jdbcTemplate, "PAYOUT_RETRY", settlement.getId())).isEmpty();
    }

    @Test
    @DisplayName("30분이 안 됐거나 7일이 넘은 정산서는 건드리지 않는다")
    void ignoresRecentAndOld() {
        var settlement = saveCompleted();

        assertThat(retryScheduler.retryStuckPayouts(SettlementFixture.NOW.plusMinutes(10))).isZero();
        assertThat(retryScheduler.retryStuckPayouts(SettlementFixture.NOW.plusDays(8))).isZero();

        assertThat(reload(settlement).getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(PayoutRequests.of(jdbcTemplate, settlement.getId())).isEmpty();
    }

    @Test
    @DisplayName("결과를 받기 전에 자동 재요청을 다시 돌리면 지급을 다시 요청하고, 결과를 받은 뒤에는 요청하지 않는다")
    void retryAgainUntilResult() {
        var settlement = saveCompleted();
        var now = SettlementFixture.NOW.plusHours(1);

        retryScheduler.retryStuckPayouts(now);
        var second = retryScheduler.retryStuckPayouts(now);

        // 중복 입금은 결제가 정산서마다 한 번만 입금해 막는다(결제 서비스 테스트)
        assertThat(second).isEqualTo(1);
        assertThat(PayoutRequests.of(jdbcTemplate, settlement.getId())).hasSize(2);
        PayoutRequests.complete(eventPublisher, transactionTemplate,
            PayoutRequests.of(jdbcTemplate, settlement.getId()).getFirst());
        assertThat(retryScheduler.retryStuckPayouts(now)).isZero();
        assertThat(PayoutRequests.of(jdbcTemplate, settlement.getId())).hasSize(2);
    }

    private Settlement saveCompleted() {
        return settlementRepository.save(SettlementFixture.settlement(10_000L));
    }

    private Settlement reload(Settlement settlement) {
        return settlementRepository.findById(settlement.getId()).orElseThrow();
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
