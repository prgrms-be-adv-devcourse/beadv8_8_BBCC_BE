package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ConcurrentTasks.runRacing;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.TwoOnSaleProducts;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 생성됨을 트랜잭션 안에서 발행해 커밋하면 상품 리스너가 재고를 차감하는지 실제 PostgreSQL로 확인한다.
 * 리스너와 그 뒤의 리스너는 발행한 트랜잭션이 커밋될 때 같은 스레드에서 돌므로, 발행이 끝나면 결과를 바로 읽을 수 있다.
 * 나가는 이벤트는 커밋된 이벤트만 모으는 OrderFlowEventRecorder로 확인한다.
 * 테스트마다 끝난 뒤 재고 불변식을 확인한다. DB를 비우는 extension보다 @AfterEach가 먼저 실행된다.
 */
@IntegrationTest
class ProductOrderEventListenerIntegrationTest {

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
    @DisplayName("커밋된 주문 생성됨을 받으면 재고를 줄이고, 차감이 커밋된 뒤 재고 차감됨을 한 번 보낸다")
    void deductAndPublishStockDeducted() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);

        publishInTransaction(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(eventRecorder.events(StockDeductedEvent.class))
            .extracting(event -> event.data().orderId())
            .containsExactly(ORDER_ID);
    }

    @Test
    @DisplayName("주문 생성됨을 보낸 트랜잭션이 롤백되면 재고를 줄이지 않고 재고 차감됨도 보내지 않는다")
    void ignoreOrderCreatedOfRolledBackTransaction() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));
            status.setRollbackOnly();
        });

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.records()).isEmpty();
        assertThat(eventRecorder.events(StockDeductedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("INV-10 같은 주문 생성됨을 두 번 받아도 재고는 한 번만 줄고 재고 차감됨도 한 번만 보낸다")
    void deductOnceForRepeatedOrderCreated() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        OrderCreatedEvent event = orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2));

        publishInTransaction(event);
        publishInTransaction(event);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(eventRecorder.events(StockDeductedEvent.class))
            .extracting(stockDeducted -> stockDeducted.data().orderId())
            .containsExactly(ORDER_ID);
    }

    @Test
    @DisplayName("INV-11 한 옵션이라도 재고가 부족하면 재고를 줄이지 않고 FAILED 기록만 남기며, 재고 차감됨과 상품 정보 변경됨을 보내지 않는다")
    void sendNoEventWhenStockIsShort() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 1);

        publishInTransaction(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy120(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(1);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "FAILED"),
            tuple(ORDER_ID, 12L, options.navy120().getId(), 2, "FAILED"));
        assertThat(eventRecorder.events(StockDeductedEvent.class)).isEmpty();
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("리스너가 예외를 던져도 주문 생성됨을 보낸 트랜잭션은 커밋된 채 남고, 재고와 차감 기록은 바뀌지 않으며 재고 차감됨도 보내지 않는다")
    @ExtendWith(OutputCaptureExtension.class)
    void keepPublisherCommittedWhenListenerThrows(CapturedOutput output) {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        // 주문상품 목록이 없는 주문 생성됨은 리스너가 목록을 읽다가 예외를 던진다
        OrderCreatedEvent broken = orderCreated(ORDER_ID, (List<OrderCreatedEvent.Item>) null);

        // 발행한 쪽이 같은 트랜잭션에서 바꾼 값이 커밋되는지 보려고 입고 3개를 함께 넣는다
        assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
            inventoryDeductionDb.restock(options.navy110(), 3);
            eventPublisher.publish(broken);
        })).doesNotThrowAnyException();

        // 리스너가 돌다가 예외를 던졌고, 그 예외가 로그로만 남았는지 확인한다
        assertThat(output.getAll()).contains("NullPointerException", "InventoryDeductionService");
        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(13);
        assertThat(inventoryDeductionDb.records()).isEmpty();
        assertThat(eventRecorder.events(StockDeductedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("INV-01 남은 1개에 1개짜리 주문 두 건이 동시에 오면 한 주문만 차감되고 다른 주문은 FAILED이며, 재고 차감됨은 차감된 주문으로 한 번만 보낸다")
    void deductOnlyOneOfTwoConcurrentOrdersForLastUnit() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(1, 5);

        // 재고 행을 잠근 채 두 주문을 출발시켜, 두 차감이 모두 그 행을 기다린 뒤 겹치게 한다
        runRacing(inventoryDeductionDb, inventoryDeductionDb.lockInventoryRow(options.navy110()), List.of(
            () -> publishInTransaction(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 1))),
            () -> publishInTransaction(orderCreated(ORDER_ID + 1, orderItem(21L, options.navy110(), 1)))));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isZero();
        List<Tuple> records = inventoryDeductionDb.records();
        assertThat(records).extracting(record -> record.toList().get(4))
            .containsExactlyInAnyOrder("DEDUCTED", "FAILED");
        Long deductedOrderId = records.stream()
            .filter(record -> "DEDUCTED".equals(record.toList().get(4)))
            .map(record -> (Long) record.toList().get(0))
            .findFirst().orElseThrow();
        assertThat(eventRecorder.events(StockDeductedEvent.class))
            .extracting(stockDeducted -> stockDeducted.data().orderId())
            .containsExactly(deductedOrderId);
    }

    @Test
    @DisplayName("SALE-08 차감한 뒤에도 재고가 남으면 상품 정보 변경됨을 보내지 않는다")
    void sendNoProductChangedWhenStockRemains() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);

        publishInTransaction(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(eventRecorder.events(ProductChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("SALE-08 옵션의 마지막 재고를 차감하면 그 상품의 상품 정보 변경됨을 한 번 보내고, 그 옵션만 품절이다")
    void sendProductChangedWhenOptionSellsOut() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(2, 5);

        publishInTransaction(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));

        assertThat(eventRecorder.events(ProductChangedEvent.class)).singleElement()
            .satisfies(changed -> assertThat(changed.data().options())
                .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
                .containsExactlyInAnyOrder(
                    tuple(options.navy110().getId(), true),
                    tuple(options.navy120().getId(), false)));
    }

    @Test
    @DisplayName("SALE-08 한 주문으로 두 상품의 옵션이 품절되면 상품마다 상품 정보 변경됨을 한 번씩 보낸다")
    void sendProductChangedPerSoldOutProduct() {
        TwoOnSaleProducts products = productOptionSaver.saveTwoOnSaleProducts(1, 1);

        publishInTransaction(orderCreated(ORDER_ID,
            orderItem(11L, products.firstNavy110(), 1),
            orderItem(12L, products.secondNavy110(), 1)));

        assertThat(eventRecorder.events(ProductChangedEvent.class))
            .extracting(changed -> changed.data().productId())
            .containsExactlyInAnyOrder(products.firstId(), products.secondId());
    }

    // 주문 저장처럼 트랜잭션 안에서 주문 생성됨을 발행하고 커밋한다
    private void publishInTransaction(OrderCreatedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
