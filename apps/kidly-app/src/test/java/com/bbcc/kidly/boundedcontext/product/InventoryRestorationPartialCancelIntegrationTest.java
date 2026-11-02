package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.cancelledItem;
import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.orderCancelled;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static com.bbcc.kidly.boundedcontext.product.OrderItemCancelledFixture.cancelledOrderItem;
import static com.bbcc.kidly.boundedcontext.product.OrderItemCancelledFixture.orderItemCancelled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderItemCancelledEvent;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
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
 * 판매자 부분 취소(주문상품 취소됨)를 받아 취소한 주문상품의 차감만 되돌리는 흐름을 실제 PostgreSQL로 확인한다(#693).
 * 차감은 서비스를 직접 불러 준비한다. 나가는 이벤트는 커밋된 이벤트만 모으는 OrderFlowEventRecorder로 확인한다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않고 발행만 트랜잭션 안에서 한다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class InventoryRestorationPartialCancelIntegrationTest {

    private static final long ORDER_ID = 100L;
    private static final long CANCELLATION_ID = 7L;

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
    @DisplayName("INV-04 판매자가 부분 취소한 주문상품들의 DEDUCTED 기록만 RESTORED로 바꾸고 그 수량을 되돌리며, 같은 주문의 다른 주문상품은 DEDUCTED 그대로다")
    void restoreOnlyCancelledOrderItems() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy120(), 1),
            orderItem(13L, options.navy110(), 1)));

        publishInTransaction(orderItemCancelled(ORDER_ID, CANCELLATION_ID,
            cancelledOrderItem(11L, options.navy110(), 2),
            cancelledOrderItem(13L, options.navy110(), 1)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(4);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "RESTORED"),
            tuple(ORDER_ID, 12L, options.navy120().getId(), 1, "DEDUCTED"),
            tuple(ORDER_ID, 13L, options.navy110().getId(), 1, "RESTORED"));
    }

    @Test
    @DisplayName("INV-08 같은 부분 취소를 두 번 받아도 재고를 한 번만 되돌린다")
    void restoreOnceWhenSamePartialCancelArrivesTwice() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy120(), 1)));
        OrderItemCancelledEvent event = orderItemCancelled(ORDER_ID, CANCELLATION_ID,
            cancelledOrderItem(11L, options.navy110(), 2));

        publishInTransaction(event);
        publishInTransaction(event);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "RESTORED"),
            tuple(ORDER_ID, 12L, options.navy120().getId(), 1, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-04 부분 취소 뒤 같은 주문의 주문 취소됨이 오면 남은 DEDUCTED만 되돌리고, 이미 RESTORED인 주문상품은 다시 되돌리지 않는다")
    void restoreOnlyRemainingWhenOrderCancelledAfterPartialCancel() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy120(), 1)));
        publishInTransaction(orderItemCancelled(ORDER_ID, CANCELLATION_ID,
            cancelledOrderItem(11L, options.navy110(), 2)));

        publishInTransaction(orderCancelled(ORDER_ID,
            cancelledItem(11L, options.navy110(), 2),
            cancelledItem(12L, options.navy120(), 1)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(5);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "RESTORED"),
            tuple(ORDER_ID, 12L, options.navy120().getId(), 1, "RESTORED"));
    }

    @Test
    @DisplayName("SALE-08 차감으로 품절된 옵션이 부분 취소로 되돌아오면 상품 정보 변경됨을 한 번 보내고, 그 옵션은 품절이 아니다")
    void sendProductChangedWhenPartialCancelReleasesSoldOut() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(2, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy120(), 1)));
        eventRecorder.clear();

        publishInTransaction(orderItemCancelled(ORDER_ID, CANCELLATION_ID,
            cancelledOrderItem(11L, options.navy110(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(2);
        assertThat(eventRecorder.events(ProductChangedEvent.class)).singleElement()
            .satisfies(changed -> assertThat(changed.data().options())
                .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
                .containsExactlyInAnyOrder(
                    tuple(options.navy110().getId(), false),
                    tuple(options.navy120().getId(), false)));
    }

    @Test
    @DisplayName("차감 기록이 없는 주문상품의 부분 취소는 FAILED를 남기지 않고 재고와 기록을 바꾸지 않으며, 도메인 사이 버그라 ERROR 로그를 남긴다")
    void changeNothingWhenPartialCancelHasNoDeduction(CapturedOutput output) {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);

        publishInTransaction(orderItemCancelled(ORDER_ID, CANCELLATION_ID,
            cancelledOrderItem(11L, options.navy110(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.records()).isEmpty();
        assertThat(output.getOut()).containsPattern("ERROR .*차감 기록이 없는 주문상품 취소됨");
    }

    @Test
    @DisplayName("INV-04 재고 부족으로 거절해 FAILED 기록만 있는 주문상품의 부분 취소는 재고와 기록을 바꾸지 않으며, 결제될 수 없는 주문이라 ERROR 로그를 남긴다")
    void changeNothingWhenPartialCancelOfRejectedOrder(CapturedOutput output) {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(1, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));

        publishInTransaction(orderItemCancelled(ORDER_ID, CANCELLATION_ID,
            cancelledOrderItem(11L, options.navy110(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(1);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "FAILED"));
        assertThat(output.getOut()).containsPattern("ERROR .*거절한 주문상품의 주문상품 취소됨");
    }

    private void publishInTransaction(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
