package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 주문 생성됨을 받아 재고를 차감하는 서비스를 실제 PostgreSQL로 확인한다. 리스너 대신 서비스를 직접 부른다.
 * 서비스가 트랜잭션을 직접 열고 커밋하므로 테스트에 @Transactional을 붙이지 않는다.
 * 테스트마다 끝난 뒤 재고 불변식을 확인한다. DB를 비우는 extension보다 @AfterEach가 먼저 실행된다.
 */
@IntegrationTest
class InventoryDeductionServiceIntegrationTest {

    private static final long ORDER_ID = 100L;
    private static final long MISSING_OPTION_ID = 999_999L;

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
    @DisplayName("INV-04 재고가 충분하면 옵션별 판매 가능 수량을 줄이고 주문상품마다 DEDUCTED 기록을 남긴다. 같은 옵션의 두 줄은 합만큼 준다")
    void deductWhenAvailable() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);

        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy110(), 3),
            orderItem(13L, options.navy120(), 1)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(5);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(4);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "DEDUCTED"),
            tuple(ORDER_ID, 12L, options.navy110().getId(), 3, "DEDUCTED"),
            tuple(ORDER_ID, 13L, options.navy120().getId(), 1, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-11 한 옵션이라도 판매 가능 수량이 부족하면 먼저 줄인 옵션까지 모두 되돌리고 주문상품마다 FAILED 기록을 남긴다")
    void rejectWhenAnyOptionIsShort() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 1);

        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(12L, options.navy120(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(1);
        assertThat(inventoryDeductionDb.records()).containsExactlyInAnyOrder(
            tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "FAILED"),
            tuple(ORDER_ID, 12L, options.navy120().getId(), 2, "FAILED"));
    }

    @Test
    @DisplayName("INV-13 판매중이 아닌 상품의 옵션이 든 주문은 재고를 줄이지 않고 주문상품마다 FAILED 기록을 남긴다")
    void rejectWhenNotOnSale() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionDb.stopSale(options.navy110());

        inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "FAILED"));
    }

    @Test
    @DisplayName("INV-10 이미 차감한 주문의 주문 생성됨을 여러 번 다시 받아도 처음 한 번 받은 것과 결과가 같다")
    void ignoreRedeliveredDeductedOrder() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        OrderCreatedEvent event = orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2));
        inventoryDeductionService.deduct(event);

        inventoryDeductionService.deduct(event);
        inventoryDeductionService.deduct(event);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-10 재고가 부족해 거절한 주문의 주문 생성됨을 재고를 채운 뒤 다시 받아도 차감하지 않고 FAILED 기록을 그대로 둔다")
    void ignoreRedeliveredRejectedOrder() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(1, 5);
        OrderCreatedEvent event = orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2));
        inventoryDeductionService.deduct(event);
        inventoryDeductionDb.restock(options.navy110(), 9);

        inventoryDeductionService.deduct(event);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "FAILED"));
    }

    @Test
    @DisplayName("INV-10 차감한 뒤 판매를 멈춘 상품의 같은 주문 생성됨을 다시 받아도 판매 상태를 다시 판단하지 않아 아무것도 바뀌지 않는다")
    void ignoreRedeliveredOrderAfterSaleStopped() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        OrderCreatedEvent event = orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2));
        inventoryDeductionService.deduct(event);
        inventoryDeductionDb.stopSale(options.navy110());

        inventoryDeductionService.deduct(event);

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-10 차감한 주문상품 ID로 수량이 다른 주문 생성됨이 오면 주문 쪽 오류로 보고 예외 없이 아무것도 바꾸지 않는다")
    void ignoreOrderItemWithDifferentContent() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);
        inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 2)));

        inventoryDeductionService.deduct(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 3)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(8);
        assertThat(inventoryDeductionDb.records())
            .containsExactly(tuple(ORDER_ID, 11L, options.navy110().getId(), 2, "DEDUCTED"));
    }

    @Test
    @DisplayName("INV-15 주문상품 ID가 겹친 주문 생성됨은 주문 쪽 오류로 보고 예외 없이 재고와 기록을 바꾸지 않는다")
    void ignoreDuplicateOrderItemIds() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);

        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            orderItem(11L, options.navy120(), 1)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.availableQty(options.navy120())).isEqualTo(5);
        assertThat(inventoryDeductionDb.records()).isEmpty();
    }

    @Test
    @DisplayName("없는 옵션 ID가 든 주문 생성됨은 차감 기록이 가리킬 옵션이 없어 예외 없이 재고와 기록을 바꾸지 않는다")
    void ignoreMissingOption() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(10, 5);

        inventoryDeductionService.deduct(orderCreated(ORDER_ID,
            orderItem(11L, options.navy110(), 2),
            new OrderCreatedEvent.Item(12L, MISSING_OPTION_ID, 1)));

        assertThat(inventoryDeductionDb.availableQty(options.navy110())).isEqualTo(10);
        assertThat(inventoryDeductionDb.records()).isEmpty();
    }
}
