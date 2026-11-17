package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.cancelledItem;
import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.orderCancelled;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.support.CommittedEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryAdjustmentService;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryRestorationService;
import com.bbcc.kidly.boundedcontext.product.app.inventory.dto.InventoryAdjustmentRequest;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class InventoryAdjustmentConcurrencyIntegrationTest {

    private static final long ORDER_ID = 100L;

    @Autowired
    private InventoryAdjustmentService inventoryAdjustmentService;

    @Autowired
    private InventoryDeductionService inventoryDeductionService;

    @Autowired
    private InventoryRestorationService inventoryRestorationService;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private InventoryDeductionDb inventoryDeductionDb;

    @Autowired
    private CommittedEventRecorder eventRecorder;

    @AfterEach
    void checkInvariants() {
        inventoryDeductionDb.assertInvariants();
    }

    @Test
    @DisplayName("INV-02 판매 가능 수량 5인 옵션에 분실 -5 조정과 1개짜리 주문 5건이 동시에 오면 한쪽만 반영되고 수량은 0이다")
    void adjustWhileDeducting() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(5, 5);
        AtomicBoolean adjusted = new AtomicBoolean();
        List<Runnable> tasks = new ArrayList<>();
        tasks.add(adjustLost(options.navy110(), 5, adjusted));
        for (int i = 1; i <= 5; i++) {
            OrderCreatedEvent event = orderCreated(ORDER_ID + i, orderItem(1000L + i, options.navy110(), 1));
            tasks.add(() -> inventoryDeductionService.deduct(event));
        }

        InventoryDeductionDb.HeldLock lock = inventoryDeductionDb.lockInventoryRow(options.navy110());
        ConcurrentTasks.runRacing(inventoryDeductionDb, lock, tasks);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isZero();
        assertThat(inventoryDeductionDb.countByStatus("DEDUCTED")).isEqualTo(adjusted.get() ? 0 : 5);
    }

    @Test
    @DisplayName("INV-02 판매 가능 수량 5인 옵션에 5개를 차감한 주문의 주문 취소됨과 분실 -5 조정이 동시에 와도 둘 다 반영된다")
    void adjustWhileRestoring() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 5)));
        AtomicBoolean adjusted = new AtomicBoolean();
        List<Runnable> tasks = List.of(
            () -> inventoryRestorationService.restore(
                orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 5))),
            adjustLost(options.navy110(), 5, adjusted));

        // 조정은 지금 판매 가능 수량(5)으로 조건이 맞아야 행을 기다린다. 0이면 기다리지 않고 바로 거절되어 둘이 겹치지 않는다
        InventoryDeductionDb.HeldLock lock = inventoryDeductionDb.lockInventoryRow(options.navy110());
        ConcurrentTasks.runRacing(inventoryDeductionDb, lock, tasks);

        assertThat(adjusted.get()).isTrue();
        assertThat(inventoryDeductionDb.countByStatus("RESTORED")).isEqualTo(1);
        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(5);
    }

    @Test
    @DisplayName("SALE-08 같은 상품의 두 옵션을 분실 조정과 주문 차감이 동시에 품절시켜도, 늦게 발생한 상품 정보 변경됨에는 두 옵션이 모두 품절이다")
    void latestProductChangedHasBothOptionsSoldOut() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(5, 1);
        eventRecorder.clear();
        AtomicBoolean adjusted = new AtomicBoolean();
        List<Runnable> tasks = List.of(
            adjustLost(options.navy110(), 5, adjusted),
            () -> inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy120(), 1))));

        InventoryDeductionDb.HeldLock lock = inventoryDeductionDb.lockProductRow(options.navy110());
        ConcurrentTasks.runRacing(inventoryDeductionDb, lock, tasks);

        assertThat(adjusted.get()).isTrue();
        List<ProductChangedEvent> changes = eventRecorder.events(ProductChangedEvent.class);
        assertThat(changes).hasSize(2);
        ProductChangedEvent latest = changes.stream()
            .max(Comparator.comparing(ProductChangedEvent::occurredAt))
            .orElseThrow();
        assertThat(latest.data().options())
            .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
            .containsExactlyInAnyOrder(
                tuple(options.navy110().getId(), true),
                tuple(options.navy120().getId(), true));
    }

    @Test
    @DisplayName("SALE-08 판매 시작이 커밋되기 전에 분실 조정으로 옵션이 품절되면, 판매 시작이 커밋된 뒤 상품 정보 변경됨을 한 번 보낸다")
    void sendProductChangedAfterConcurrentSaleStartCommits() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(5, 5);
        AtomicBoolean adjusted = new AtomicBoolean();
        InventoryDeductionDb.HeldLock saleStart = inventoryDeductionDb.holdUncommittedSaleStart(options.navy110());
        eventRecorder.clear();

        ConcurrentTasks.runRacing(inventoryDeductionDb, saleStart, List.of(adjustLost(options.navy110(), 5, adjusted)));

        assertThat(adjusted.get()).isTrue();
        assertThat(eventRecorder.events(ProductChangedEvent.class)).singleElement()
            .satisfies(changed -> assertThat(changed.data().options())
                .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
                .containsExactlyInAnyOrder(
                    tuple(options.navy110().getId(), true),
                    tuple(options.navy120().getId(), false)));
    }

    private Runnable adjustLost(ProductOption option, int qty, AtomicBoolean adjusted) {
        return () -> {
            try {
                inventoryAdjustmentService.adjust(
                    SELLER_ID, option.getId(), new InventoryAdjustmentRequest(-qty, "LOST"));
                adjusted.set(true);
            } catch (BusinessException e) {
            }
        };
    }
}
