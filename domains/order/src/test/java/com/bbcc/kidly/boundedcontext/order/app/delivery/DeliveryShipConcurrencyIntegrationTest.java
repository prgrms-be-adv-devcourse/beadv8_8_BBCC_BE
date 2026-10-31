package com.bbcc.kidly.boundedcontext.order.app.delivery;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.MarkPaidCommands;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 판매자 발송 동시 ×2(#446, 실제 PostgreSQL). 테스트에 @Transactional을 붙이지 않는다(작업마다 자기 트랜잭션으로 커밋).
 * 반복마다 구매자를 바꿔 새 주문으로 시작하고, 결과(배송 상태 · 송장 · 상태 이력 수)로 불변식을 본다.
 * 진 쪽은 낙관적 락(공통 409 CONFLICT) 또는 이긴 쪽 커밋 뒤 시작해 상태 검사의 409 ALREADY_SHIPPED로 끝나는 것이 지금 동작이다.
 */
@IntegrationTest
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class DeliveryShipConcurrencyIntegrationTest {

    private static final int REPEAT = 10;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @Test
    @DisplayName("같은 배송에 발송 두 번이 동시에 와도 한쪽만 SHIPPING · 송장은 이긴 쪽 값 · 발송 이력 1줄, 진 쪽은 낙관적 락 또는 ALREADY_SHIPPED")
    void shipTwiceConcurrently() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long buyerId = 1_000L + round;
            Long orderId = placeAndPay(buyerId);
            Long deliveryId = deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId).getFirst().getId();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> deliveryService.ship(SELLER_ID, deliveryId, "CJ", "1111111111"),
                () -> deliveryService.ship(SELLER_ID, deliveryId, "HANJIN", "2222222222")),
                DeliveryShipConcurrencyIntegrationTest::isAlreadyShipped);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽(낙관적 락 · ALREADY_SHIPPED)", round).isEqualTo(1);
            Delivery delivery = deliveryRepository.findByIdAndSellerId(deliveryId, SELLER_ID).orElseThrow();
            assertThat(delivery.getStatus()).as("%d회차", round).isEqualTo(Delivery.Status.SHIPPING);
            assertThat(delivery.getCarrier() + ":" + delivery.getTrackingNumber()).as("%d회차 송장", round)
                .isIn("CJ:1111111111", "HANJIN:2222222222");
            assertThat(jdbcTemplate.queryForObject("select version from orders.delivery where id = ?", Long.class,
                deliveryId)).as("%d회차 배송 버전(생성 0 → 발송 1)", round).isEqualTo(1L);
            assertThat(historyCount(orderId, "DELIVERY", "SHIPPING")).as("%d회차 발송 이력", round).isEqualTo(1);
            assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(Order.Status.PAID);
        }
    }

    private static boolean isAlreadyShipped(Throwable error) {
        return error instanceof BusinessException business
            && business.getErrorCode() == OrderErrorCode.ALREADY_SHIPPED;
    }

    private Long placeAndPay(Long buyerId) {
        cartService.addItem(buyerId, 1L, null, 1);
        Long orderId = orderService.place(buyerId, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"))).orderId();
        orderService.markPaid(MarkPaidCommands.matching(orderService.getOrder(buyerId, orderId), buyerId, 500L));
        deliveryService.createFor(orderId);
        return orderId;
    }

    private long historyCount(Long orderId, String targetType, String toStatus) {
        return jdbcTemplate.queryForObject("select count(*) from orders.order_status_history "
            + "where order_id = ? and target_type = ? and to_status = ?", Long.class, orderId, targetType, toStatus);
    }
}
