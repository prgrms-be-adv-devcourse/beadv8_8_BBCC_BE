package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    // 주문 저장처럼 트랜잭션 안에서 주문 생성됨을 발행하고 커밋한다
    private void publishInTransaction(OrderCreatedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
