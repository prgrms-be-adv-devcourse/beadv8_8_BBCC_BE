package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 재고 차감을 실제 스레드 여러 개로 동시에 실행해 실제 PostgreSQL의 행 잠금과 UNIQUE 제약으로 결과를 확인한다.
 * 모든 스레드가 준비된 뒤 출발 신호 하나로 함께 시작한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class InventoryDeductionConcurrencyIntegrationTest {

    private static final long ORDER_ID = 100L;

    @Autowired
    private InventoryDeductionService inventoryDeductionService;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private InventoryDeductionDb inventoryDeductionDb;

    @AfterEach
    void checkInvariants() {
        inventoryDeductionDb.assertInvariants();
    }

    @Test
    @DisplayName("INV-01 판매 가능 수량 10인 옵션에 1개짜리 주문 20개가 동시에 오면 정확히 10개만 차감되고 나머지는 FAILED이며 수량은 0이다")
    void deductManyOrdersConcurrently() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        List<Runnable> orders = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            OrderCreatedEvent event = orderCreated(ORDER_ID + i, orderItem(1000L + i, options.navy110(), 1));
            orders.add(() -> inventoryDeductionService.deduct(event));
        }

        runConcurrently(orders);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isZero();
        assertThat(inventoryDeductionDb.countByStatus("DEDUCTED")).isEqualTo(10);
        assertThat(inventoryDeductionDb.countByStatus("FAILED")).isEqualTo(10);
    }

    @Test
    @DisplayName("INV-10 같은 주문 생성됨을 다섯 스레드가 동시에 처리해도 두 옵션 모두 재고가 한 번만 줄고 주문상품마다 기록은 한 줄이다")
    void deductSameEventConcurrently() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        OrderCreatedEvent event = orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy120(), 1));
        List<Runnable> deliveries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            deliveries.add(() -> inventoryDeductionService.deduct(event));
        }

        // 먼저 차감하는 110의 재고 행을 잠가, 다섯 스레드가 모두 기존 기록 확인을 지난 뒤 차감에서 겹치게 한다
        runRacing(inventoryDeductionDb.lockInventoryRow(firstDeducted(options)), deliveries);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(4);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "DEDUCTED"),
            tuple(ORDER_ID, 12L, options.navy120().getId(), 1, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-12 두 옵션을 서로 반대 순서로 담은 두 주문이 동시에 와도 옵션 ID 오름차순으로 줄여 교착 상태 없이 모두 차감된다")
    void deductOppositeOptionOrdersConcurrently() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 10);
        OrderCreatedEvent lowFirst = orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 1), orderItem(12L, options.navy120(), 1));
        OrderCreatedEvent highFirst = orderCreated(ORDER_ID + 1,
            orderItem(21L, options.navy120(), 1), orderItem(22L, options.navy110(), 1));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<?>> results = new ArrayList<>();
        try {
            // ID가 작은 옵션의 재고 행을 잠근 채 두 주문을 차례로 출발시킨다. 담은 순서대로 줄인다면 두 번째 주문은
            // ID가 큰 옵션을 먼저 잠그고 기다리므로, 잠금을 풀면 두 주문이 서로의 행을 기다리는 교착이 생긴다
            try (InventoryDeductionDb.HeldLock lock = inventoryDeductionDb.lockInventoryRow(firstDeducted(options))) {
                results.addAll(startTogether(executor, List.of(() -> inventoryDeductionService.deduct(lowFirst))));
                inventoryDeductionDb.awaitLockWaiters(1);
                results.addAll(startTogether(executor, List.of(() -> inventoryDeductionService.deduct(highFirst))));
                inventoryDeductionDb.awaitLockWaiters(2);
            }
            awaitAll(results);
        } finally {
            executor.shutdown();
        }

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(8);
        assertThat(inventoryDeductionDb.countByStatus("DEDUCTED")).isEqualTo(4);
    }

    @Test
    @DisplayName("INV-10 재고가 한 번분뿐인 같은 주문 생성됨을 두 스레드가 동시에 처리하면, 늦은 쪽은 FAILED를 남기려다 UNIQUE에 막히고 예외 없이 끝난다")
    void deductSameEventWithStockForOnceConcurrently() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(3, 5);
        OrderCreatedEvent event = orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2));

        runRacing(inventoryDeductionDb.lockInventoryRow(options.navy110()),
            List.of(() -> inventoryDeductionService.deduct(event), () -> inventoryDeductionService.deduct(event)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(1);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-10 판매중이 아니어서 거절할 같은 주문 생성됨을 두 스레드가 동시에 처리해도 FAILED 기록은 한 줄이고 예외 없이 끝난다")
    void rejectSameEventConcurrently() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionDb.stopSale(options.navy110());
        OrderCreatedEvent event = orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2));

        runRacing(inventoryDeductionDb.lockDeductionTable(),
            List.of(() -> inventoryDeductionService.deduct(event), () -> inventoryDeductionService.deduct(event)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "FAILED"));
    }

    // 차감은 옵션 ID 오름차순이라 ID가 작은 옵션의 재고 행을 먼저 잠근다
    private static ProductOption firstDeducted(OnSaleOptions options) {
        if (options.navy110().getId() < options.navy120().getId()) {
            return options.navy110();
        }
        return options.navy120();
    }

    // 테스트가 잠금을 쥔 채 작업을 모두 출발시키고, 모든 작업이 그 잠금을 기다리는 것을 확인한 뒤 잠금을 푼다.
    // 그래서 작업들이 기존 기록 확인을 모두 지난 다음에 겹치는 경쟁이 매번 생긴다
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

    // 모든 작업이 스레드에서 준비된 뒤 출발 신호 하나로 함께 시작시키고, 결과를 기다릴 Future를 돌려준다
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
