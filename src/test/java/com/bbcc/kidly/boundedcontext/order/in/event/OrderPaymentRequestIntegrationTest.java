package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문하기 → order.created → (상품 도메인 대신 직접) stock.deducted → 리스너 → order.payment-requested.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 * ApplicationEvents는 테스트 스레드에서 발행된 이벤트만 기록하므로 HTTP 대신 주문하기 서비스를 직접 부른다.
 */
@IntegrationTest
@RecordApplicationEvents
class OrderPaymentRequestIntegrationTest {

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private OrderService orderService;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Test
    @DisplayName("주문하면 order.created, 재고 차감 이벤트를 받으면 order.payment-requested를 한 번만 발행한다")
    void placeThenRequestPaymentOnce() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));

        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, 1L, 2, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));

        assertThat(events.stream(OrderCreatedEvent.class).toList()).hasSize(1);
        OrderCreatedEvent created = events.stream(OrderCreatedEvent.class).toList().get(0);
        assertThat(created.data().orderId()).isEqualTo(order.orderId());
        assertThat(created.data().items()).hasSize(1);

        publishStockDeducted(order.orderId());

        List<OrderPaymentRequestedEvent> requested = events.stream(OrderPaymentRequestedEvent.class).toList();
        assertThat(requested).hasSize(1);
        assertThat(requested.get(0).data().orderId()).isEqualTo(order.orderId());
        assertThat(requested.get(0).data().amount()).isEqualTo(50_000L);
        assertThat(requested.get(0).data().items()).hasSize(1);
        assertThat(requested.get(0).data().items().get(0).orderItemId())
            .isEqualTo(created.data().items().get(0).orderItemId());
        assertThat(requested.get(0).data().items().get(0).amount()).isEqualTo(50_000L);

        publishStockDeducted(order.orderId());

        assertThat(events.stream(OrderPaymentRequestedEvent.class).toList()).hasSize(1);
    }

    private void publishStockDeducted(Long orderId) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(new StockDeductedEvent(
            UUID.randomUUID(), LocalDateTime.now(), new StockDeductedEvent.Data(orderId))));
    }
}
