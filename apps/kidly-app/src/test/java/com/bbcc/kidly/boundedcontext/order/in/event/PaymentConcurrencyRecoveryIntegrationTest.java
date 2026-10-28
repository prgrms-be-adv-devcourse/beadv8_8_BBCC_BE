package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 결제 완료 처리의 동시 요청 · 실패 복구. 테스트에 @Transactional을 붙이지 않는다(작업마다 자기 트랜잭션으로 커밋).
 * 재고 차감됨은 보내지 않아 실제 결제가 끼지 않는다. 결제 완료는 리스너 메서드를 직접 부른다(자체 트랜잭션).
 * 반복마다 구매자를 바꿔 새 주문 · 새 장바구니로 시작하고, 이벤트는 그 주문 ID로 걸러 센다.
 */
@IntegrationTest
class PaymentConcurrencyRecoveryIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentConcurrencyRecoveryIntegrationTest.class);
    private static final Long OTHER_SELLER_ID = 3L;
    private static final int REPEAT = 10;
    private static final String DELIVERY_UNIQUE = "uk_delivery_order_seller";

    @Autowired
    private PaymentEventListener paymentEventListener;

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
    private CartItemRepository cartItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @BeforeEach
    void setUp() {
        // 테스트마다 주문 ID가 1부터 다시 시작해(RESTART IDENTITY) 다른 테스트의 같은 orderId 이벤트가 섞이지 않게 비운다
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("결제 완료 ×2와 결제 마감 만료가 동시에 와도 하나만 반영되고 배송 · 장바구니 · 취소 이벤트가 그 결과와 맞는다")
    void paymentCompletedTwiceAndExpiry() throws InterruptedException {
        int paid = 0;
        int expired = 0;
        for (int round = 1; round <= REPEAT; round++) {
            Long buyerId = 1_000L + round;
            OrderDetail order = placeWithCart(buyerId);
            jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
                LocalDateTime.now().minusMinutes(1), order.orderId());
            PaymentCompletedEvent event = paymentCompleted(order, buyerId);

            // 진 쪽 정상 예외: 낙관적 락 · 배송 (order_id, seller_id) 유니크 위반.
            // 배송은 저장 즉시 INSERT되고 주문 버전 검사는 커밋 때라, 결제 완료끼리 겹치면 진 쪽이 유니크 위반으로 먼저 끝난다(전부 되돌려짐).
            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> paymentEventListener.onPaymentCompleted(event),
                () -> paymentEventListener.onPaymentCompleted(event),
                () -> orderService.cancelForPaymentExpiry(order.orderId())),
                PaymentConcurrencyRecoveryIntegrationTest::isDeliveryUniqueViolation);

            assertThat(outcome.unexpected()).as("정상으로 본 예외 말고 다른 예외(%d회차)", round).isEmpty();
            Order stored = orderRepository.findById(order.orderId()).orElseThrow();
            List<Delivery> deliveries = deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId());
            int cartLines = cartItemRepository.findAllByMemberIdOrderByIdDesc(buyerId).size();
            long cancelledEvents = cancelledEventsOf(order.orderId());
            LOG.info("[#137] 결제 완료 ×2 + 만료 {}회차: 결과 {}, 진 쪽(낙관적 락 · 배송 유니크) {}회",
                round, stored.getStatus(), outcome.conflicts());
            assertThat(recoveryCountOf(order.orderId())).as("진 쪽은 보관하지 않음(%d회차)", round).isZero();

            if (stored.getStatus() == Order.Status.PAID) {
                paid++;
                assertThat(deliveries).extracting(Delivery::getSellerId)
                    .containsExactlyInAnyOrder(SELLER_ID, OTHER_SELLER_ID);
                assertThat(cartLines).isZero();
                assertThat(cancelledEvents).isZero();
            } else if (stored.getStatus() == Order.Status.CANCELLED) {
                expired++;
                assertThat(stored.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_EXPIRED);
                assertThat(deliveries).isEmpty();
                assertThat(cartLines).isEqualTo(2);
                assertThat(cancelledEvents).isEqualTo(1);
            } else {
                fail("결제완료도 취소도 아님(%d회차): %s", round, stored.getStatus());
            }
        }
        LOG.info("[#137] 결제 완료 ×2 + 만료 {}회: PAID {} · CANCELLED {}", REPEAT, paid, expired);
    }

    @Test
    @DisplayName("결제 완료 처리 중 배송 생성이 실패하면 결제완료 · 배송 · 장바구니 정리가 모두 되돌려지고, 같은 이벤트로 다시 처리된다")
    void rollsBackAndRetries() {
        Long buyerId = 2_000L;
        OrderDetail order = placeWithCart(buyerId);
        Delivery existing = deliveryRepository.save(Delivery.create(order.orderId(), SELLER_ID));
        PaymentCompletedEvent event = paymentCompleted(order, buyerId);

        assertThatThrownBy(() -> paymentEventListener.onPaymentCompleted(event))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(orderRepository.findById(order.orderId()).orElseThrow().getStatus())
            .isEqualTo(Order.Status.PENDING_PAYMENT);
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId()))
            .extracting(Delivery::getId).containsExactly(existing.getId());
        assertThat(cartItemRepository.findAllByMemberIdOrderByIdDesc(buyerId)).hasSize(2);
        assertThat(recoveryCountOf(order.orderId())).as("배송 유니크 위반은 진 쪽이라 보관하지 않음").isZero();

        jdbcTemplate.update("delete from orders.delivery where id = ?", existing.getId());
        paymentEventListener.onPaymentCompleted(event);

        assertThat(orderRepository.findById(order.orderId()).orElseThrow().getStatus())
            .isEqualTo(Order.Status.PAID);
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId()))
            .extracting(Delivery::getSellerId).containsExactlyInAnyOrder(SELLER_ID, OTHER_SELLER_ID);
        assertThat(cartItemRepository.findAllByMemberIdOrderByIdDesc(buyerId)).isEmpty();
    }

    /**
     * 판매자 A 상품 · 판매자 B 상품을 장바구니에 담고 그대로 주문한다(자녀 없음). 결제대기로 남는다.
     */
    private OrderDetail placeWithCart(Long buyerId) {
        cartService.addItem(buyerId, 1L, null, 1);
        cartService.addItem(buyerId, 2L, null, 1);
        return orderService.place(buyerId, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L), new PlaceOrderCommand.Item(2L, null, 1, 30_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private PaymentCompletedEvent paymentCompleted(OrderDetail order, Long buyerId) {
        return new PaymentCompletedEvent(UUID.randomUUID(), LocalDateTime.now(), new PaymentCompletedEvent.Data(
            500L, order.orderId(), buyerId, order.totalAmount(),
            order.items().stream()
                .map(item -> new PaymentCompletedEvent.Item(item.orderItemId(), item.amount()))
                .toList(),
            LocalDateTime.now()));
    }

    private static boolean isDeliveryUniqueViolation(Throwable error) {
        if (!(error instanceof DataIntegrityViolationException)) {
            return false;
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(DELIVERY_UNIQUE)) {
                return true;
            }
        }
        return false;
    }

    private long recoveryCountOf(Long orderId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from orders.payment_recovery where order_id = ?", Long.class, orderId);
    }

    private long cancelledEventsOf(Long orderId) {
        return recorder.events(OrderCancelledEvent.class).stream()
            .filter(event -> event.data().orderId().equals(orderId))
            .count();
    }
}
