package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

        runConcurrently(deliveries);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(4);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "DEDUCTED"),
            tuple(ORDER_ID, 12L, options.navy120().getId(), 1, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-12 두 옵션을 서로 반대 순서로 담은 주문 10개가 동시에 와도 교착 상태 없이 모두 차감된다")
    void deductOppositeOptionOrdersConcurrently() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(100, 100);
        List<Runnable> orders = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            long orderItemId = 1000L + i * 10;
            OrderCreatedEvent event = i % 2 == 0
                ? orderCreated(ORDER_ID + i,
                    orderItem(orderItemId, options.navy110(), 1), orderItem(orderItemId + 1, options.navy120(), 1))
                : orderCreated(ORDER_ID + i,
                    orderItem(orderItemId, options.navy120(), 1), orderItem(orderItemId + 1, options.navy110(), 1));
            orders.add(() -> inventoryDeductionService.deduct(event));
        }

        runConcurrently(orders);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(90);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(90);
        assertThat(inventoryDeductionDb.countByStatus("DEDUCTED")).isEqualTo(20);
    }

    // 작업마다 스레드를 하나씩 두고, 모두 준비되면 출발 신호로 함께 시작한다. 어느 작업이든 예외가 나면 테스트가 실패한다
    private static void runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
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
            for (Future<?> result : results) {
                result.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
