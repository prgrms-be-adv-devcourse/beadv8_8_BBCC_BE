package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.cancelledItem;
import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.orderCancelled;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 취소됨을 받아 그 주문에서 차감한 재고를 되돌리는 흐름을 실제 PostgreSQL로 확인한다.
 * 주문 생성됨 리스너가 아직 없어서 차감은 서비스를 직접 불러 준비한다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않고 발행만 트랜잭션 안에서 한다.
 */
@IntegrationTest
class InventoryRestorationIntegrationTest {

    private static final long ORDER_ID = 100L;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private InventoryDeductionService inventoryDeductionService;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private InventoryDeductionDb inventoryDeductionDb;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void checkInvariants() {
        inventoryDeductionDb.assertInvariants();
    }

    @Test
    @DisplayName("INV-07 주문 취소됨을 발행한 트랜잭션이 커밋되면 리스너가 그 주문의 DEDUCTED 기록을 모두 RESTORED로 바꾸고 두 옵션의 수량을 되돌린다")
    void restoreAfterCommit() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy110(), 3),
            orderItem(13L, options.navy120(), 1)));

        publishInTransaction(orderCancelled(ORDER_ID,
            cancelledItem(11L, options.navy110(), 2),
            cancelledItem(12L, options.navy110(), 3),
            cancelledItem(13L, options.navy120(), 1)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(5);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "RESTORED"),
            tuple(ORDER_ID, 12L, options.navy110().getId(), 3, "RESTORED"),
            tuple(ORDER_ID, 13L, options.navy120().getId(), 1, "RESTORED"));
    }

    @Test
    @DisplayName("주문 취소됨을 발행한 트랜잭션이 롤백되면 재고와 차감 기록이 그대로다")
    void keepStockAfterRollback() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 2)));
            status.setRollbackOnly();
        });

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-08 같은 주문 취소됨을 두 번 받아도 재고를 한 번만 되돌린다")
    void restoreOnceWhenSameEventArrivesTwice() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));
        OrderCancelledEvent event = orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 2));

        publishInTransaction(event);
        publishInTransaction(event);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "RESTORED"));
    }

    @Test
    @DisplayName("INV-14 차감한 뒤 판매중지된 상품이어도 주문이 취소되면 재고를 되돌린다")
    void restoreEvenWhenSaleStopped() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));
        inventoryDeductionDb.stopSale(options.navy110());

        publishInTransaction(orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "RESTORED"));
    }

    @Test
    @DisplayName("INV-09 차감 기록이 없는 주문의 주문 취소됨이 먼저 오면 주문상품마다 FAILED를 남기고, 나중에 온 주문 생성됨은 차감하지 않는다")
    void recordFailedWhenCancelledBeforeCreated() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);

        publishInTransaction(orderCancelled(ORDER_ID,
            cancelledItem(11L, options.navy110(), 2),
            cancelledItem(12L, options.navy120(), 1)));
        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy120(), 1)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(5);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "FAILED"),
            tuple(ORDER_ID, 12L, options.navy120().getId(), 1, "FAILED"));
    }

    @Test
    @DisplayName("INV-09 재고 부족으로 거절해 FAILED 기록만 있는 주문의 주문 취소됨은 재고와 기록을 바꾸지 않는다")
    void keepFailedOrderAsIs() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(1, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));

        publishInTransaction(orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(1);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "FAILED"));
    }

    @Test
    @DisplayName("INV-07 두 번째 옵션의 재고를 되돌리다 실패하면 먼저 되돌린 옵션까지 모두 그대로고 기록도 DEDUCTED다")
    void keepAllWhenSecondOptionFails() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy120(), 1)));
        ProductOption first = firstInOptionIdOrder(options);
        ProductOption second = first == options.navy110() ? options.navy120() : options.navy110();
        int firstQty = inventoryDeductionDb.availableQty(first);
        int secondQty = inventoryDeductionDb.availableQty(second);
        // 나중에 되돌리는 옵션의 수량을 int 최댓값으로 두어, 더하는 UPDATE가 DB의 정수 범위를 넘어 실패하게 한다
        setAvailableQty(second, Integer.MAX_VALUE);
        try {
            publishInTransaction(orderCancelled(ORDER_ID,
                cancelledItem(11L, options.navy110(), 2),
                cancelledItem(12L, options.navy120(), 1)));

            assertThat(inventoryDeductionDb.availableQty(first)).isEqualTo(firstQty);
            assertThat(inventoryDeductionDb.availableQty(second)).isEqualTo(Integer.MAX_VALUE);
            assertThat(inventoryDeductionDb.countByStatus("DEDUCTED")).isEqualTo(2);
        } finally {
            // 단언이 실패해도 끝난 뒤 재고 불변식을 확인할 수 있게 바꾼 수량을 되돌린다
            setAvailableQty(second, secondQty);
        }
    }

    private ProductOption firstInOptionIdOrder(OnSaleOptions options) {
        if (options.navy110().getId() < options.navy120().getId()) {
            return options.navy110();
        }
        return options.navy120();
    }

    private void setAvailableQty(ProductOption option, int qty) {
        jdbcTemplate.update("update product.inventory set available_qty = ? where product_option_id = ?",
            qty, option.getId());
    }

    private void publishInTransaction(OrderCancelledEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
