package com.bbcc.kidly.boundedcontext.order.app.delivery;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 배송 완료와 주문상품 배송 기록이 한 트랜잭션인지 본다. 테스트에 @Transactional을 붙이지 않는다.
 * 주문상품 기록 단계의 실패는 주문 저장소 스파이로 주문 조회를 실패시켜 만든다.
 */
@IntegrationTest
class DeliveryCompleteTransactionIntegrationTest {

    private static final Long BUYER_ID = 6_000L;
    private static final Long OTHER_SELLER_ID = 3L;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @MockitoSpyBean
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", "120", 30_000L, 2L, "ON_SALE", false, OCCURRED_AT)));
    }

    @AfterEach
    void resetSpy() {
        reset(orderRepository);
    }

    @Test
    @DisplayName("배송 완료하면 배송 DELIVERED와 그 판매자 몫 주문상품 배송 기록이 함께 남고, 다른 판매자 몫은 그대로다")
    void completesTogether() {
        OrderDetail order = paidOrderWithDeliveries();
        Long deliveryId = shippedDeliveryOf(order.orderId());

        deliveryService.complete(SELLER_ID, deliveryId);

        assertThat(deliveryOf(order.orderId()).getStatus()).isEqualTo(Delivery.Status.DELIVERED);
        List<OrderDetail.Item> items = orderService.getOrder(BUYER_ID, order.orderId()).items();
        assertThat(items).filteredOn(item -> item.sellerId().equals(SELLER_ID))
            .singleElement().satisfies(item -> assertThat(item.deliveredAt()).isNotNull());
        assertThat(items).filteredOn(item -> item.sellerId().equals(OTHER_SELLER_ID))
            .singleElement().satisfies(item -> assertThat(item.deliveredAt()).isNull());
    }

    @Test
    @DisplayName("주문상품 기록 단계에서 실패하면 배송 완료도 되돌려져 SHIPPING이고 주문상품 배송 기록도 없다")
    void rollsBackDeliveryWhenRecordingFails() {
        OrderDetail order = paidOrderWithDeliveries();
        Long deliveryId = shippedDeliveryOf(order.orderId());
        doThrow(new IllegalStateException("주입한 실패")).when(orderRepository).findById(order.orderId());

        assertThatThrownBy(() -> deliveryService.complete(SELLER_ID, deliveryId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("주입한 실패");

        reset(orderRepository);
        assertThat(deliveryOf(order.orderId()).getStatus()).isEqualTo(Delivery.Status.SHIPPING);
        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).items())
            .allSatisfy(item -> assertThat(item.deliveredAt()).isNull());
    }

    /**
     * 판매자 A 상품 · 판매자 B 상품 주문 → 결제완료 → 판매자별 배송 준비.
     */
    private OrderDetail paidOrderWithDeliveries() {
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L), new PlaceOrderCommand.Item(2L, null, 1, 30_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        orderService.markPaid(order.orderId(), 500L, order.totalAmount());
        deliveryService.createFor(order.orderId());
        return order;
    }

    private Long shippedDeliveryOf(Long orderId) {
        Long deliveryId = deliveryOf(orderId).getId();
        deliveryService.ship(SELLER_ID, deliveryId, "CJ", "1234567890");
        return deliveryId;
    }

    private Delivery deliveryOf(Long orderId) {
        return deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId).stream()
            .filter(delivery -> delivery.getSellerId().equals(SELLER_ID))
            .findFirst()
            .orElseThrow();
    }
}
