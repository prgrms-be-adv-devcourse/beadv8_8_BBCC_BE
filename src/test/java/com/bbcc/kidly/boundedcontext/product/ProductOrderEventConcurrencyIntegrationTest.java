package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ConcurrentTasks.runRacing;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 생성됨 두 건을 실제 스레드 두 개에서 리스너 경로로 동시에 처리해, 품절 전환 때 나가는 상품 정보 변경됨을 확인한다.
 * 스레드는 ConcurrentTasks로 함께 출발시키고, 테스트가 쥔 잠금으로 두 처리가 겹치게 한다.
 * 나가는 이벤트는 커밋된 이벤트만 모으는 OrderFlowEventRecorder로 확인한다. 테스트마다 끝난 뒤 재고 불변식을 확인한다.
 */
@IntegrationTest
class ProductOrderEventConcurrencyIntegrationTest {

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
    @DisplayName("SALE-08 남은 1개에 주문 두 건이 동시에 와도 상품 정보 변경됨은 차감한 주문에서 한 번만 보낸다")
    void sendProductChangedOnceWhenTwoOrdersRaceForLastUnit() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(1, 5);

        // 재고 행을 잠근 채 두 주문을 출발시켜, 두 차감이 모두 그 행을 기다린 뒤 겹치게 한다
        runRacing(inventoryDeductionDb, inventoryDeductionDb.lockInventoryRow(options.navy110()), List.of(
            () -> publishInTransaction(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 1))),
            () -> publishInTransaction(orderCreated(ORDER_ID + 1, orderItem(21L, options.navy110(), 1)))));

        assertThat(eventRecorder.events(ProductChangedEvent.class)).singleElement()
            .satisfies(changed -> assertThat(changed.data().options())
                .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
                .contains(tuple(options.navy110().getId(), true)));
    }

    // 복사본은 발생 시각이 더 늦은 이벤트만 반영하므로, 늦게 발생한 이벤트가 두 옵션의 품절을 모두 담아야 한다.
    // 차감 기록 표를 잠가 두 주문이 각자 재고를 줄인 뒤 기록 저장에서 기다리게 하고, 잠금을 풀어 품절 여부를 거의 동시에 읽게 한다.
    // 읽은 뒤 커밋하기 전의 틈은 테스트가 붙잡을 수 없어서, 여러 번 반복해 겹치는 경우를 만든다
    @RepeatedTest(value = 20, name = "{displayName} ({currentRepetition}/{totalRepetitions})")
    @DisplayName("SALE-08 같은 상품의 두 옵션을 두 주문이 동시에 품절시켜도, 늦게 발생한 상품 정보 변경됨에는 두 옵션이 모두 품절이다")
    void latestProductChangedHasBothOptionsSoldOut() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(1, 1);

        runRacing(inventoryDeductionDb, inventoryDeductionDb.lockDeductionTable(), List.of(
            () -> publishInTransaction(orderCreated(ORDER_ID, orderItem(11L, options.navy110(), 1))),
            () -> publishInTransaction(orderCreated(ORDER_ID + 1, orderItem(21L, options.navy120(), 1)))));

        ProductChangedEvent latest = eventRecorder.events(ProductChangedEvent.class).stream()
            .max(Comparator.comparing(ProductChangedEvent::occurredAt))
            .orElseThrow();
        assertThat(latest.data().options())
            .extracting(ProductChangedEvent.Option::optionId, ProductChangedEvent.Option::soldOut)
            .containsExactlyInAnyOrder(
                tuple(options.navy110().getId(), true),
                tuple(options.navy120().getId(), true));
    }

    // 주문 저장처럼 트랜잭션 안에서 주문 생성됨을 발행하고 커밋한다
    private void publishInTransaction(OrderCreatedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
