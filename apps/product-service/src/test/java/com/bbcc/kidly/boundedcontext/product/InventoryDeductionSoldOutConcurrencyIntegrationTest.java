package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ConcurrentTasks.runRacing;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.support.CommittedEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class InventoryDeductionSoldOutConcurrencyIntegrationTest {

    private static final long ORDER_ID = 100L;

    @Autowired
    private InventoryDeductionService inventoryDeductionService;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private InventoryDeductionDb inventoryDeductionDb;

    @Autowired
    private CommittedEventRecorder eventRecorder;

    @BeforeEach
    void clearEvents() {
        eventRecorder.clear();
    }

    @AfterEach
    void checkInvariants() {
        inventoryDeductionDb.assertInvariants();
    }

    @Test
    @DisplayName("SALE-08 남은 1개에 주문 두 건이 동시에 와도 상품 정보 변경됨은 차감한 주문에서 한 번만 보낸다")
    void sendProductChangedOnceWhenTwoOrdersRaceForLastUnit() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(1, 5);

        runRacing(inventoryDeductionDb, inventoryDeductionDb.lockInventoryRow(options.navy110()), List.of(
            () -> inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 1))),
            () -> inventoryDeductionService.deduct(orderCreated(ORDER_ID + 1, orderItem(21L, options.navy110(), 1)))));

        assertThat(eventRecorder.events(ProductChangedEvent.class)).singleElement()
            .satisfies(changed -> assertThat(changed.data().options())
                .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
                .contains(tuple(options.navy110().getId(), true)));
    }

    @RepeatedTest(value = 20, name = "{displayName} ({currentRepetition}/{totalRepetitions})")
    @DisplayName("SALE-08 같은 상품의 두 옵션을 두 주문이 동시에 품절시켜도, 늦게 발생한 상품 정보 변경됨에는 두 옵션이 모두 품절이다")
    void latestProductChangedHasBothOptionsSoldOut() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(1, 1);

        runRacing(inventoryDeductionDb, inventoryDeductionDb.lockDeductionTable(), List.of(
            () -> inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 1))),
            () -> inventoryDeductionService.deduct(orderCreated(ORDER_ID + 1, orderItem(21L, options.navy120(), 1)))));

        ProductChangedEvent latest = eventRecorder.events(ProductChangedEvent.class).stream()
            .max(Comparator.comparing(ProductChangedEvent::occurredAt))
            .orElseThrow();
        assertThat(latest.data().options())
            .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
            .containsExactlyInAnyOrder(
                tuple(options.navy110().getId(), true),
                tuple(options.navy120().getId(), true));
    }
}
