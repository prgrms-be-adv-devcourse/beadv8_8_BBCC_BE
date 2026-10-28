package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ConcurrentTasks.awaitAll;
import static com.bbcc.kidly.boundedcontext.product.ConcurrentTasks.runConcurrently;
import static com.bbcc.kidly.boundedcontext.product.ConcurrentTasks.runRacing;
import static com.bbcc.kidly.boundedcontext.product.ConcurrentTasks.startTogether;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 재고 차감을 실제 스레드 여러 개로 동시에 실행해 실제 PostgreSQL의 행 잠금과 UNIQUE 제약으로 결과를 확인한다.
 * 스레드는 ConcurrentTasks로 함께 출발시킨다. 테스트에 @Transactional을 붙이지 않는다.
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

    @Autowired
    private OrderFlowEventRecorder eventRecorder;

    @BeforeEach
    void clearEvents() {
        eventRecorder.clear();
    }

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
    @DisplayName("INV-10 같은 주문 생성됨을 다섯 스레드가 동시에 처리해도 두 옵션 모두 재고가 한 번만 줄고, 주문상품마다 기록은 한 줄이며 재고 차감됨도 한 번만 보낸다")
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
        runRacing(inventoryDeductionDb, inventoryDeductionDb.lockInventoryRow(firstDeducted(options)), deliveries);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(4);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "DEDUCTED"),
            tuple(ORDER_ID, 12L, options.navy120().getId(), 1, "DEDUCTED"));
        assertThat(eventRecorder.events(StockDeductedEvent.class))
            .extracting(stockDeducted -> stockDeducted.data().orderId())
            .containsExactly(ORDER_ID);
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
    @DisplayName("INV-10 재고가 한 번분뿐인 같은 주문 생성됨을 두 스레드가 동시에 처리하면, 늦은 쪽은 FAILED를 남기려다 UNIQUE에 막히고 재고 부족함도 보내지 않는다")
    void deductSameEventWithStockForOnceConcurrently() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(3, 5);
        OrderCreatedEvent event = orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2));

        runRacing(inventoryDeductionDb, inventoryDeductionDb.lockInventoryRow(options.navy110()),
            List.of(() -> inventoryDeductionService.deduct(event), () -> inventoryDeductionService.deduct(event)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(1);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "DEDUCTED"));
        // 늦은 쪽의 거절이 새면 재고를 이미 줄인 주문이 취소되므로, 재고 부족함이 나가지 않는지 함께 본다
        assertThat(eventRecorder.events(StockDeductedEvent.class)).hasSize(1);
        assertThat(eventRecorder.events(StockDeductionFailedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("INV-10 판매중이 아니어서 거절할 같은 주문 생성됨을 두 스레드가 동시에 처리해도 FAILED 기록은 한 줄이고 예외 없이 끝난다")
    void rejectSameEventConcurrently() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionDb.stopSale(options.navy110());
        OrderCreatedEvent event = orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2));

        runRacing(inventoryDeductionDb, inventoryDeductionDb.lockDeductionTable(),
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
}
