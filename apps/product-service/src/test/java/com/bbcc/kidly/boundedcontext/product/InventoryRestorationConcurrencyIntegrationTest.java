package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ConcurrentTasks.runConcurrently;
import static com.bbcc.kidly.boundedcontext.product.ConcurrentTasks.runRacing;
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
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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

        runRacing(inventoryDeductionDb, inventoryDeductionDb.lockInventoryRow(firstRestored(options)), deliveries);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(5);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "RESTORED"),
            tuple(ORDER_ID, 12L, options.navy120().getId(), 1, "RESTORED"));
    }

    private static ProductOption firstRestored(OnSaleOptions options) {
        if (options.navy110().getId() < options.navy120().getId()) {
            return options.navy110();
        }
        return options.navy120();
    }
}
