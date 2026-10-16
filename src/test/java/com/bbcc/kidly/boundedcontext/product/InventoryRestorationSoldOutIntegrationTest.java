package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.cancelledItem;
import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.orderCancelled;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 취소됨으로 재고를 되돌릴 때 품절이 풀린 옵션의 상품 정보 변경됨이 나가는지 실제 PostgreSQL로 확인한다.
 * 차감은 서비스를 직접 불러 준비하고, 차감이 보낸 이벤트는 지운 뒤 주문 취소됨을 트랜잭션 안에서 발행한다.
 * 나가는 이벤트는 커밋된 이벤트만 모으는 OrderFlowEventRecorder로 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class InventoryRestorationSoldOutIntegrationTest {

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
    @DisplayName("SALE-08 차감으로 품절된 옵션이 주문 취소로 되돌아오면 그 상품의 상품 정보 변경됨을 한 번 보내고, 그 옵션은 품절이 아니다")
    void sendProductChangedWhenRestoreReleasesSoldOut() {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(2, 5);
        deductAndClearEvents(orderItem(11L, options.navy110(), 2));

        publishInTransaction(orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 2)));

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

    // 주문 취소처럼 트랜잭션 안에서 주문 취소됨을 발행하고 커밋한다
    private void publishInTransaction(OrderCancelledEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
