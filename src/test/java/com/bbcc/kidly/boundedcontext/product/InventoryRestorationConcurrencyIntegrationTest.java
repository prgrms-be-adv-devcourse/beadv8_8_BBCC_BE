package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.cancelledItem;
import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.orderCancelled;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryRestorationService;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 재고 복구를 실제 스레드 여러 개로 동시에 실행해 실제 PostgreSQL의 행 잠금과 UNIQUE 제약으로 결과를 확인한다.
 * 모든 스레드가 준비된 뒤 출발 신호 하나로 함께 시작한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class InventoryRestorationConcurrencyIntegrationTest {

    private static final long ORDER_ID = 100L;

    @Autowired
    private InventoryDeductionService inventoryDeductionService;

    @Autowired
    private InventoryRestorationService inventoryRestorationService;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private InventoryDeductionDb inventoryDeductionDb;

    @AfterEach
    void checkInvariants() {
        inventoryDeductionDb.assertInvariants();
    }

    @Test
    @DisplayName("INV-09 같은 주문의 주문 생성됨과 주문 취소됨을 10쌍 동시에 처리해도 판매 가능 수량은 처음과 같고, 주문마다 기록은 모두 RESTORED이거나 모두 FAILED다")
    void deductAndCancelSameOrderConcurrently() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 10);
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            long orderId = ORDER_ID + i;
            long firstItemId = orderId * 10;
            OrderCreatedEvent created = orderCreated(orderId,
                orderItem(firstItemId, options.navy110(), 1), orderItem(firstItemId + 1, options.navy120(), 1));
            OrderCancelledEvent cancelled = orderCancelled(orderId,
                cancelledItem(firstItemId, options.navy110(), 1), cancelledItem(firstItemId + 1, options.navy120(), 1));
            tasks.add(() -> inventoryDeductionService.deduct(created));
            tasks.add(() -> inventoryRestorationService.restore(cancelled));
        }

        runConcurrently(tasks);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(10);
        Map<Long, Set<String>> statusesByOrder = new HashMap<>();
        for (Tuple record : inventoryDeductionDb.records()) {
            statusesByOrder.computeIfAbsent((Long) record.toList().get(0), id -> new HashSet<>())
                .add((String) record.toList().get(4));
        }
        assertThat(statusesByOrder).hasSize(10);
        assertThat(statusesByOrder.values()).allMatch(
            statuses -> statuses.equals(Set.of("RESTORED")) || statuses.equals(Set.of("FAILED")),
            "주문마다 기록이 모두 RESTORED이거나 모두 FAILED");
    }

    @Test
    @DisplayName("INV-08 같은 주문 취소됨을 다섯 스레드가 동시에 처리해도 두 옵션 모두 재고를 한 번만 되돌린다")
    void restoreSameEventConcurrently() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2), orderItem(12L, options.navy120(), 1)));
        OrderCancelledEvent event = orderCancelled(ORDER_ID,
            cancelledItem(11L, options.navy110(), 2), cancelledItem(12L, options.navy120(), 1));
        List<Runnable> deliveries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            deliveries.add(() -> inventoryRestorationService.restore(event));
        }

        // 먼저 되돌리는 옵션의 재고 행을 잠가, 다섯 스레드가 모두 기록을 읽은 뒤 복구에서 겹치게 한다.
        // 한 스레드는 그 재고 행을, 나머지 넷은 그 스레드가 바꾼 차감 기록 행을 기다린다
        runRacing(inventoryDeductionDb.lockInventoryRow(firstRestored(options)), deliveries);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(5);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "RESTORED"),
            tuple(ORDER_ID, 12L, options.navy120().getId(), 1, "RESTORED"));
    }

    // 복구는 옵션 ID 오름차순이라 ID가 작은 옵션의 재고 행을 먼저 바꾼다
    private static ProductOption firstRestored(OnSaleOptions options) {
        if (options.navy110().getId() < options.navy120().getId()) {
            return options.navy110();
        }
        return options.navy120();
    }

    // 테스트가 잠금을 쥔 채 작업을 모두 출발시키고, 모든 작업이 잠금을 기다리는 것을 확인한 뒤 잠금을 푼다
    private void runRacing(InventoryDeductionDb.HeldLock lock, List<Runnable> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<?>> results;
            try (lock) {
                results = startTogether(executor, tasks);
                inventoryDeductionDb.awaitLockWaiters(tasks.size());
            }
            awaitAll(results);
        } finally {
            executor.shutdown();
        }
    }

    // 작업마다 스레드를 하나씩 두고, 모두 준비되면 출발 신호로 함께 시작한다. 어느 작업이든 예외가 나면 테스트가 실패한다
    private static void runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            awaitAll(startTogether(executor, tasks));
        } finally {
            executor.shutdown();
        }
    }

    private static List<Future<?>> startTogether(ExecutorService executor, List<Runnable> tasks)
        throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (Runnable task : tasks) {
            results.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("출발 신호를 받지 못했습니다");
                }
                task.run();
                return null;
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).as("모든 스레드가 준비됨").isTrue();
        start.countDown();
        return results;
    }

    // 모든 작업이 끝날 때까지 기다린 뒤 처음 난 예외를 던진다. 먼저 실패한 작업 때문에 남은 작업을 끊지 않아,
    // 테스트가 끝난 뒤 다른 스레드가 DB를 건드리지 않는다
    private static void awaitAll(List<Future<?>> results) throws Exception {
        Exception first = null;
        for (Future<?> result : results) {
            try {
                result.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
