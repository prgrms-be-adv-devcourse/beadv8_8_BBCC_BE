package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.boundedcontext.settlement.in.SettlementOrderEventListener;
import com.bbcc.kidly.boundedcontext.settlement.out.JpaSettlementItemRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 요청이 실제 스레드로 동시에 들어와도 정산이 한 번만 반영되는지 확인한다.
 * 작업마다 자기 스레드 · 자기 트랜잭션으로 커밋하고, 출발 신호 하나로 함께 시작한다.
 */
@IntegrationTest
class SettlementConcurrencyIntegrationTest {

    private static final int THREADS = 5;
    private static final long TIMEOUT_SECONDS = 60;
    private static final Long ADMIN_ID = 3L;
    private static final Long SELLER_ID = 10L;
    // 수동 실행은 미래 기준일을 거부하므로 기준일은 이번 달 1일(오늘 또는 지난 날)로 잡는다
    private static final LocalDate CUTOFF_ON = LocalDate.now().withDayOfMonth(1);
    private static final LocalDateTime CONFIRMED_AT = CUTOFF_ON.atStartOfDay().minusDays(1);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private SettlementRunService settlementRunService;

    @Autowired
    private SettlementOrderEventListener settlementOrderEventListener;

    // 저장된 정산 항목 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaSettlementItemRepository jpaSettlementItemRepository;

    @Autowired
    private SettlementItemRepository settlementItemRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 구매확정을 동시에 받아도 정산 대상은 1건이다")
    void samePurchaseConfirmedAtOnce() throws Exception {
        var event = new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), CONFIRMED_AT,
            new OrderItemPurchaseConfirmedEvent.Data(101L, 1L, 2L, SELLER_ID, 3L, 4L, 5L,
                "120", 1, 10_000L, CONFIRMED_AT));

        // 이벤트로 발행하면 같은 이벤트를 받는 다른 도메인 리스너가 먼저 유니크에서 막혀 스레드가 줄을 서므로,
        // 정산 리스너를 바로 불러 정산 쪽 중복 방지(조회 + order_item_id 유니크)만 겨룬다
        var outcomes = runAtOnce(() -> {
            settlementOrderEventListener.on(event);
            return null;
        });

        assertThat(outcomes).filteredOn(outcome -> outcome.error() != null)
            .allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOf(DataIntegrityViolationException.class));
        assertThat(jpaSettlementItemRepository.findAll()).singleElement()
            .extracting(SettlementItem::getOrderItemId).isEqualTo(101L);
    }

    @Test
    @DisplayName("같은 기준일로 동시에 실행하면 한쪽만 정산하고 나머지는 409이며 정산서 · 지급 요청은 1건이다")
    void sameCutoffAtOnce() throws Exception {
        settlementItemRepository.save(SettlementFixture.item(201L, SELLER_ID, 10_000L, CONFIRMED_AT));

        var outcomes = runAtOnce(() -> settlementRunService.runManually(CUTOFF_ON, ADMIN_ID));

        assertThat(outcomes).filteredOn(outcome -> outcome.error() == null).singleElement()
            .satisfies(outcome -> assertThat(outcome.value().status()).isEqualTo("COMPLETED"));
        assertThat(outcomes).filteredOn(outcome -> outcome.error() != null)
            .hasSize(THREADS - 1)
            .allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.CONFLICT)));
        assertThat(count("SELECT count(*) FROM settlement.settlement")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment.payout")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 지급 완료 결과를 동시에 받아도 정산서 상태는 한 번만 바뀐다")
    void samePayoutCompletedAtOnce() throws Exception {
        var settlement = settlementRepository.save(SettlementFixture.settlement(10_000L));
        var versionBefore = settlement.getVersion();
        var paidAt = LocalDateTime.of(2026, 11, 1, 9, 0);
        var event = new PaymentPayoutCompletedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentPayoutCompletedEvent.Data(settlement.getId(), SettlementFixture.SELLER_ID,
                settlement.getSettlementAmount(), 1L, paidAt));

        // 진 쪽 리스너는 낙관적 락 충돌로 롤백되거나 이미 지급 완료라 아무것도 하지 않는다
        var outcomes = runAtOnce(() -> publishInTransaction(event));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
        var reloaded = settlementRepository.findById(settlement.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(reloaded.getPaidAt()).isEqualTo(paidAt);
        assertThat(reloaded.getVersion()).isEqualTo(versionBefore + 1);
    }

    private Void publishInTransaction(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
        return null;
    }

    private Integer count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private <T> List<Outcome<T>> runAtOnce(Callable<T> task) throws InterruptedException {
        var executor = Executors.newFixedThreadPool(THREADS);
        var start = new CountDownLatch(1);
        try {
            var futures = IntStream.range(0, THREADS)
                .mapToObj(i -> executor.submit(() -> {
                    start.await();
                    return task.call();
                }))
                .toList();
            start.countDown();
            var outcomes = new ArrayList<Outcome<T>>();
            for (Future<T> future : futures) {
                outcomes.add(outcomeOf(future));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private static <T> Outcome<T> outcomeOf(Future<T> future) throws InterruptedException {
        try {
            return new Outcome<>(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), null);
        } catch (ExecutionException e) {
            return new Outcome<>(null, e.getCause());
        } catch (TimeoutException e) {
            return new Outcome<>(null, e);
        }
    }

    private record Outcome<T>(T value, Throwable error) {
    }
}
