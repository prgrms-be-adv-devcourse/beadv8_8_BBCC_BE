package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ConcurrentTasks.runRacing;
import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.cancelledItem;
import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.orderCancelled;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryRestorationService;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 취소됨으로 재고를 되돌릴 때 품절이 풀린 옵션의 상품 정보 변경됨이 나가는지 실제 PostgreSQL로 확인한다.
 * 차감은 서비스를 직접 불러 준비하고, 차감이 보낸 이벤트는 지운 뒤 주문 취소됨을 트랜잭션 안에서 발행한다.
 * 나가는 이벤트는 커밋된 이벤트만 모으는 OrderFlowEventRecorder로 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class InventoryRestorationSoldOutIntegrationTest {

    private static final long ORDER_ID = 100L;
    private static final long OTHER_ORDER_ID = 200L;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private InventoryDeductionService inventoryDeductionService;

    @Autowired
    private InventoryRestorationService inventoryRestorationService;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private InventoryDeductionDb inventoryDeductionDb;

    @Autowired
    private OrderFlowEventRecorder eventRecorder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearEvents() {
        eventRecorder.clear();
    }

    @AfterEach
    void checkInvariants() {
        inventoryDeductionDb.assertInvariants();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ON_SALE", "STOPPED"})
    @DisplayName("SALE-08 차감으로 품절된 옵션이 주문 취소로 되돌아오면 판매중지된 상품이어도 상품 정보 변경됨을 한 번 보내고, 그 옵션은 품절이 아니다")
    void sendProductChangedWhenRestoreReleasesSoldOut(String status) {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(2, 5);
        deductAndClearEvents(orderItem(11L, options.navy110(), 2));
        // 차감한 뒤에 판매중지돼도 주문이 취소되면 재고를 되돌린다 (INV-14)
        if (status.equals("STOPPED")) {
            inventoryDeductionDb.stopSale(options.navy110());
        }

        publishInTransaction(orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(2);
        assertThat(eventRecorder.events(ProductChangedEvent.class)).singleElement()
            .satisfies(changed -> assertThat(changed.data().options())
                .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
                .containsExactlyInAnyOrder(
                    tuple(options.navy110().getId(), false),
                    tuple(options.navy120().getId(), false)));
    }

    @Test
    @DisplayName("SALE-08 차감한 뒤에도 재고가 남아 있던 옵션을 되돌리면 상품 정보 변경됨을 보내지 않는다")
    void sendNothingWhenRestoredOptionWasNotSoldOut() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        deductAndClearEvents(orderItem(11L, options.navy110(), 2));

        publishInTransaction(orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("SALE-08 같은 상품의 두 옵션이 모두 품절이었다가 한 주문 취소로 되돌아오면 상품 정보 변경됨을 한 번 보내고, 두 옵션 모두 품절이 아니다")
    void sendOneProductChangedWhenTwoOptionsOfSameProductAreReleased() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(1, 1);
        deductAndClearEvents(orderItem(11L, options.navy110(), 1), orderItem(12L, options.navy120(), 1));

        publishInTransaction(orderCancelled(ORDER_ID,
            cancelledItem(11L, options.navy110(), 1),
            cancelledItem(12L, options.navy120(), 1)));

        assertThat(eventRecorder.events(ProductChangedEvent.class)).singleElement()
            .satisfies(changed -> assertThat(changed.data().options())
                .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
                .containsExactlyInAnyOrder(
                    tuple(options.navy110().getId(), false),
                    tuple(options.navy120().getId(), false)));
    }

    @Test
    @DisplayName("INV-08 품절된 옵션을 되돌리는 같은 주문 취소됨을 다섯 스레드가 동시에 처리해도 재고를 한 번만 되돌리고 상품 정보 변경됨도 한 번만 보낸다")
    void sendProductChangedOnceWhenSameEventIsRestoredConcurrently() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(2, 5);
        deductAndClearEvents(orderItem(11L, options.navy110(), 2));
        OrderCancelledEvent event = orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 2));
        List<Runnable> deliveries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            deliveries.add(() -> inventoryRestorationService.restore(event));
        }

        // 재고 행을 잠가 다섯 스레드가 모두 DEDUCTED 기록을 읽은 뒤 복구에서 겹치게 한다.
        // 한 스레드는 그 재고 행을, 나머지 넷은 그 스레드가 바꾼 차감 기록 행을 기다린다
        runRacing(inventoryDeductionDb, inventoryDeductionDb.lockInventoryRow(options.navy110()), deliveries);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(2);
        assertThat(eventRecorder.events(ProductChangedEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("INV-07 품절된 두 옵션 중 두 번째 옵션의 재고를 되돌리다 실패하면 두 옵션 모두 품절인 채로 남고 상품 정보 변경됨도 보내지 않는다")
    void sendNothingWhenRestoreFails() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(1, 1);
        deductAndClearEvents(orderItem(11L, options.navy110(), 1), orderItem(12L, options.navy120(), 1));
        ProductOption first = firstInOptionIdOrder(options);
        ProductOption second = first == options.navy110() ? options.navy120() : options.navy110();
        // 나중에 되돌리는 옵션의 수량을 int 최댓값으로 두어, 더하는 UPDATE가 DB의 정수 범위를 넘어 실패하게 한다
        setAvailableQty(second, Integer.MAX_VALUE);
        try {
            publishInTransaction(orderCancelled(ORDER_ID,
                cancelledItem(11L, options.navy110(), 1),
                cancelledItem(12L, options.navy120(), 1)));

            assertThat(inventoryDeductionDb.availableQty(first)).isZero();
            assertThat(inventoryDeductionDb.countByStatus("DEDUCTED")).isEqualTo(2);
            assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
        } finally {
            // 단언이 실패해도 끝난 뒤 재고 불변식을 확인할 수 있게 바꾼 수량을 되돌린다
            setAvailableQty(second, 0);
        }
    }

    @Test
    @DisplayName("품절된 옵션을 되돌리는 주문 취소됨을 발행한 트랜잭션이 롤백되면 재고를 되돌리지 않고 상품 정보 변경됨도 보내지 않는다")
    void sendNothingWhenPublisherRollsBack() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(2, 5);
        deductAndClearEvents(orderItem(11L, options.navy110(), 2));

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 2)));
            status.setRollbackOnly();
        });

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isZero();
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("SALE-08 품절된 옵션을 되돌리는 서로 다른 두 주문의 취소를 동시에 처리하면 품절이 풀린 쪽만 상품 정보 변경됨을 한 번 보내고, 그 옵션은 품절이 아니다")
    void sendProductChangedOnceWhenTwoOrdersAreRestoredConcurrently() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(2, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 1)));
        inventoryDeductionService.deduct(orderCreated(OTHER_ORDER_ID, orderItem(21L, options.navy110(), 1)));
        eventRecorder.clear();
        OrderCancelledEvent first = orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 1));
        OrderCancelledEvent second = orderCancelled(OTHER_ORDER_ID, cancelledItem(21L, options.navy110(), 1));

        // 재고 행을 잠가 두 주문이 모두 차감 기록을 RESTORED로 바꾼 뒤 같은 재고 행에서 겹치게 한다
        runRacing(inventoryDeductionDb, inventoryDeductionDb.lockInventoryRow(options.navy110()), List.of(
            () -> inventoryRestorationService.restore(first),
            () -> inventoryRestorationService.restore(second)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(2);
        assertThat(eventRecorder.events(ProductChangedEvent.class)).singleElement()
            .satisfies(changed -> assertThat(changed.data().options())
                .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
                .containsExactlyInAnyOrder(
                    tuple(options.navy110().getId(), false),
                    tuple(options.navy120().getId(), false)));
    }

    // 주문 생성됨으로 차감하고, 차감이 보낸 이벤트는 지워 주문 취소됨이 보낸 이벤트만 남게 한다
    private void deductAndClearEvents(OrderCreatedEvent.Item... items) {
        inventoryDeductionService.deduct(orderCreated(ORDER_ID, items));
        eventRecorder.clear();
    }

    // 복구는 옵션 ID 오름차순이라 ID가 작은 옵션의 재고 행을 먼저 바꾼다
    private static ProductOption firstInOptionIdOrder(OnSaleOptions options) {
        if (options.navy110().getId() < options.navy120().getId()) {
            return options.navy110();
        }
        return options.navy120();
    }

    private void setAvailableQty(ProductOption option, int qty) {
        jdbcTemplate.update("update product.inventory set available_qty = ? where product_option_id = ?",
            qty, option.getId());
    }

    // 주문 취소처럼 트랜잭션 안에서 주문 취소됨을 발행하고 커밋한다
    private void publishInTransaction(OrderCancelledEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
