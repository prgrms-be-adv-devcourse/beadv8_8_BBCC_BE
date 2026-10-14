package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 직접 구매확정 · 자동 구매확정. 시간은 실행 시각 기준 상대값, 기간은 설정(Order.Policy)에서 읽는다.
 * 스케줄러는 테스트 프로필에서 저절로 돌지 않게 주기를 아주 길게 두고, 메서드를 직접 부른다.
 */
@IntegrationTest
@RecordApplicationEvents
class OrderConfirmIntegrationTest {

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderScheduler orderScheduler;

    @Autowired
    private Order.Policy orderPolicy;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("배송 완료된 주문상품을 직접 확정하면 CONFIRMED · 이벤트 1번, 다시 확정해도 이벤트가 늘지 않는다")
    void confirmsByBuyerOnce() {
        OrderDetail order = deliveredOrder(LocalDateTime.now());
        Long orderItemId = order.items().get(0).orderItemId();

        OrderDetail.Item first = orderService.confirmByBuyer(BUYER_ID, order.orderId(), orderItemId);
        OrderDetail.Item second = orderService.confirmByBuyer(BUYER_ID, order.orderId(), orderItemId);

        assertThat(first.status()).isEqualTo("CONFIRMED");
        assertThat(first.confirmType()).isEqualTo("DIRECT");
        assertThat(second.confirmedAt()).isEqualTo(first.confirmedAt());
        List<OrderItemPurchaseConfirmedEvent> confirmed = events.stream(OrderItemPurchaseConfirmedEvent.class).toList();
        assertThat(confirmed).hasSize(1);
        assertThat(confirmed.get(0).data().orderItemId()).isEqualTo(orderItemId);
        assertThat(confirmed.get(0).data().orderId()).isEqualTo(order.orderId());
        assertThat(confirmed.get(0).data().childId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("배송 완료 후 기간이 지난 주문상품은 스케줄러가 AUTO로 확정하고 이벤트를 발행한다")
    void autoConfirmsAfterPeriod() {
        OrderDetail order = deliveredOrder(LocalDateTime.now().minus(orderPolicy.autoConfirmAfter()).minusMinutes(1));

        orderScheduler.autoConfirm();

        OrderDetail.Item item = itemOf(order);
        assertThat(item.status()).isEqualTo("CONFIRMED");
        assertThat(item.confirmType()).isEqualTo("AUTO");
        assertThat(events.stream(OrderItemPurchaseConfirmedEvent.class).toList()).hasSize(1);
    }

    @Test
    @DisplayName("기간이 안 지난 주문상품은 스케줄러를 돌려도 그대로다")
    void keepsBeforePeriod() {
        OrderDetail order = deliveredOrder(LocalDateTime.now().minus(orderPolicy.autoConfirmAfter()).plusMinutes(1));

        orderScheduler.autoConfirm();

        assertThat(itemOf(order).status()).isEqualTo("ORDERED");
        assertThat(events.stream(OrderItemPurchaseConfirmedEvent.class).toList()).isEmpty();
    }

    @Test
    @DisplayName("조회를 거치지 않고 자동 확정을 바로 불러도, 기간 전이면 도메인이 막아 그대로다")
    void domainKeepsBeforePeriod() {
        OrderDetail order = deliveredOrder(LocalDateTime.now().minus(orderPolicy.autoConfirmAfter()).plusMinutes(1));

        orderService.confirmAutomatically(order.orderId(), order.items().get(0).orderItemId());

        assertThat(itemOf(order).status()).isEqualTo("ORDERED");
        assertThat(events.stream(OrderItemPurchaseConfirmedEvent.class).toList()).isEmpty();
    }

    /**
     * 주문 → 결제완료 → 판매자 배송 완료(deliveredAt). 이벤트 대신 서비스를 바로 불러 상태만 만든다.
     */
    private OrderDetail deliveredOrder(LocalDateTime deliveredAt) {
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, 1L, 2, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        orderService.markPaid(order.orderId(), 500L);
        orderService.recordDelivered(order.orderId(), SELLER_ID, deliveredAt);
        return order;
    }

    private OrderDetail.Item itemOf(OrderDetail order) {
        return orderService.getOrder(BUYER_ID, order.orderId()).items().get(0);
    }
}
