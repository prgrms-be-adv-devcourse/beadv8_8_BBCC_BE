package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.cancelledItem;
import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.orderCancelled;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 리스너가 주문 생성됨의 재고 차감을 거절하면 재고 부족함을 보내는지 실제 PostgreSQL로 확인한다.
 * 리스너는 발행한 트랜잭션이 커밋될 때 같은 스레드에서 돌므로, 발행이 끝나면 결과를 바로 읽을 수 있다.
 * 나가는 이벤트는 커밋된 이벤트만 모으는 OrderFlowEventRecorder로 확인한다.
 */
@IntegrationTest
class ProductStockDeductionFailedIntegrationTest {

    private static final long ORDER_ID = 100L;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
    @DisplayName("INV-11 재고가 모자라면 재고를 줄이지 않고 주문상품마다 FAILED를 남기며, 모자란 주문상품의 사유를 담은 재고 부족함을 한 번 보낸다")
    void publishStockDeductionFailedWhenStockIsShort() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 1);

        publishInTransaction(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy120(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(1);
        assertThat(inventoryDeductionDb.countByStatus("FAILED")).isEqualTo(2);
        assertThat(eventRecorder.events(StockDeductionFailedEvent.class)).singleElement()
            .satisfies(failed -> {
                assertThat(failed.data().orderId()).isEqualTo(ORDER_ID);
                assertThat(failed.data().reason()).isEqualTo(StockDeductionFailureCode.OUT_OF_STOCK);
                assertThat(failed.data().failures())
                    .extracting(StockDeductionFailedEvent.Failure::orderItemId, StockDeductionFailedEvent.Failure::reason)
                    .containsExactly(tuple(12L, StockDeductionFailureCode.OUT_OF_STOCK));
            });
    }

    @Test
    @DisplayName("INV-11 모자란 옵션이 여럿이면 첫 부족에서 멈추지 않고 모자란 옵션의 주문상품을 모두 재고 부족함에 담는다")
    void includeEveryShortOptionInStockDeductionFailed() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(1, 1);

        publishInTransaction(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy120(), 2)));

        assertThat(eventRecorder.events(StockDeductionFailedEvent.class)).singleElement()
            .satisfies(failed -> assertThat(failed.data().failures())
                .extracting(StockDeductionFailedEvent.Failure::orderItemId, StockDeductionFailedEvent.Failure::reason)
                .containsExactly(
                    tuple(11L, StockDeductionFailureCode.OUT_OF_STOCK),
                    tuple(12L, StockDeductionFailureCode.OUT_OF_STOCK)));
    }

    @Test
    @DisplayName("INV-13 판매중이 아닌 상품의 주문이면 재고를 줄이지 않고 PRODUCT_NOT_ON_SALE를 담은 재고 부족함을 한 번 보낸다")
    void publishStockDeductionFailedWhenProductIsNotOnSale() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionDb.stopSale(options.navy110());

        publishInTransaction(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.countByStatus("FAILED")).isEqualTo(1);
        assertThat(eventRecorder.events(StockDeductionFailedEvent.class)).singleElement()
            .satisfies(failed -> {
                assertThat(failed.data().reason()).isEqualTo(StockDeductionFailureCode.PRODUCT_NOT_ON_SALE);
                assertThat(failed.data().failures())
                    .extracting(StockDeductionFailedEvent.Failure::orderItemId, StockDeductionFailedEvent.Failure::reason)
                    .containsExactly(tuple(11L, StockDeductionFailureCode.PRODUCT_NOT_ON_SALE));
            });
    }

    @Test
    @DisplayName("INV-10 같은 주문 생성됨을 두 번 받아도 거절한 주문의 재고 부족함은 한 번만 보낸다")
    void publishStockDeductionFailedOnceForRepeatedOrderCreated() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(1, 5);
        OrderCreatedEvent event = orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2));

        publishInTransaction(event);
        publishInTransaction(event);

        assertThat(eventRecorder.events(StockDeductionFailedEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("INV-09 주문 취소됨이 먼저 온 주문은 FAILED만 남기고, 나중에 온 주문 생성됨에도 재고 부족함을 보내지 않는다")
    void sendNoStockDeductionFailedWhenCancelledBeforeCreated() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);

        publishInTransaction(orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 2)));
        publishInTransaction(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.countByStatus("FAILED")).isEqualTo(1);
        assertThat(eventRecorder.events(StockDeductionFailedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("차감에 성공하면 재고 차감됨만 보내고 재고 부족함은 보내지 않는다")
    void sendNoStockDeductionFailedWhenDeducted() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);

        publishInTransaction(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));

        assertThat(eventRecorder.events(StockDeductedEvent.class)).hasSize(1);
        assertThat(eventRecorder.events(StockDeductionFailedEvent.class)).isEmpty();
    }

    // 주문 저장처럼 트랜잭션 안에서 주문 이벤트를 발행하고 커밋한다
    private void publishInTransaction(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
