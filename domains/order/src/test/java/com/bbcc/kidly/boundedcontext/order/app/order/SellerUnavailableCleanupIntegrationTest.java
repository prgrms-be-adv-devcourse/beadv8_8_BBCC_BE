package com.bbcc.kidly.boundedcontext.order.app.order;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryService;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.MarkPaidCommands;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 판매 불가 판매자 정리(#1121): seller.changed의 상태가 ACTIVE가 아니면 그 판매자의 배송 전 주문을 정리한다.
 * 결제대기 → 주문 전체 취소(SELLER_UNAVAILABLE, order.cancelled), 결제완료 · 배송 준비 → 그 판매자 몫 취소(order-item.cancelled),
 * 배송 중 · 배송 완료 · 다른 판매자 몫은 그대로. 다시 받아도 결과 · 이벤트가 같다. 판매자 이벤트는 Kafka 리스너와 같은 순서로 보낸다
 * (복사본 반영 트랜잭션이 커밋된 뒤 정리, {@link OrderEventDelivery}).
 */
@IntegrationTest
class SellerUnavailableCleanupIntegrationTest {

    private static final Long BUYER_ID = 9_100L;
    private static final Long SELLER_ID = 51L;
    private static final Long OTHER_SELLER_ID = 52L;
    private static final Long SELLER_OPTION = 501L;
    private static final Long OTHER_OPTION = 502L;
    private static final long PRICE = 20_000L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long paymentId = 700L;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(product(SELLER_OPTION, SELLER_ID)));
        productReplicaRepository.save(ReplicaProduct.create(product(OTHER_OPTION, OTHER_SELLER_ID)));
        eventDelivery.deliver(sellerChanged(SELLER_ID, SellerStatus.ACTIVE, OCCURRED_AT));
        eventDelivery.deliver(sellerChanged(OTHER_SELLER_ID, SellerStatus.ACTIVE, OCCURRED_AT));
    }

    @Test
    @DisplayName("결제대기는 주문 전체 취소(SELLER_UNAVAILABLE, order.cancelled), 결제완료 · 배송 준비는 그 판매자 몫 취소"
        + "(order-item.cancelled · 배송 취소), 배송 중 · 배송 완료는 그대로")
    void cleansUpUndispatchedOrders() {
        Long pending = place(SELLER_OPTION).orderId();
        Long ready = paid(SELLER_OPTION);
        Long shipping = paid(SELLER_OPTION);
        ship(shipping, SELLER_ID);
        Long delivered = paid(SELLER_OPTION);
        deliveryService.complete(SELLER_ID, ship(delivered, SELLER_ID));
        clearOutbox();

        eventDelivery.deliver(sellerChanged(SELLER_ID, SellerStatus.SUSPENDED, OCCURRED_AT.plusDays(1)));

        assertThat(order(pending)).containsEntry("status", "CANCELLED")
            .containsEntry("cancel_reason", "SELLER_UNAVAILABLE");
        assertThat(outboxReasons("order.cancelled", pending)).containsExactly("SELLER_UNAVAILABLE");
        assertThat(items(ready)).allSatisfy(item -> assertThat(item).containsEntry("status", "CANCELLED")
            .containsEntry("cancel_reason", "SELLER_UNAVAILABLE"));
        assertThat(deliveryStatus(ready, SELLER_ID)).isEqualTo(Delivery.Status.CANCELLED);
        assertThat(outboxReasons("order-item.cancelled", ready)).containsExactly("SELLER_UNAVAILABLE");
        // 그 판매자 몫이 주문 전부라 주문도 취소되고, 판매자 스스로 취소와 구분되게 주문 사유도 SELLER_UNAVAILABLE
        assertThat(order(ready)).containsEntry("status", "CANCELLED")
            .containsEntry("cancel_reason", "SELLER_UNAVAILABLE");
        assertThat(history(ready)).contains("SYSTEM:seller.changed");
        for (Long untouched : List.of(shipping, delivered)) {
            assertThat(order(untouched)).containsEntry("status", "PAID");
            assertThat(items(untouched)).allSatisfy(item -> assertThat(item).containsEntry("status", "ORDERED"));
        }
        assertThat(deliveryStatus(shipping, SELLER_ID)).isEqualTo(Delivery.Status.SHIPPING);
        assertThat(deliveryStatus(delivered, SELLER_ID)).isEqualTo(Delivery.Status.DELIVERED);
    }

    @Test
    @DisplayName("한 주문에 두 판매자가 섞이면 그 판매자 몫만 취소하고 주문은 결제완료 그대로, 다른 판매자만의 주문은 건드리지 않는다")
    void mixedOrderAndOtherSeller() {
        Long mixed = paid(SELLER_OPTION, OTHER_OPTION);
        Long otherPending = place(OTHER_OPTION).orderId();
        Long otherPaid = paid(OTHER_OPTION);
        clearOutbox();

        eventDelivery.deliver(sellerChanged(SELLER_ID, SellerStatus.CLOSED, OCCURRED_AT.plusDays(1)));

        assertThat(order(mixed)).containsEntry("status", "PAID");
        assertThat(itemStatusBySeller(mixed)).containsEntry(SELLER_ID, "CANCELLED").containsEntry(OTHER_SELLER_ID,
            "ORDERED");
        assertThat(deliveryStatus(mixed, SELLER_ID)).isEqualTo(Delivery.Status.CANCELLED);
        assertThat(deliveryStatus(mixed, OTHER_SELLER_ID)).isEqualTo(Delivery.Status.READY);
        assertThat(order(otherPending)).containsEntry("status", "PENDING_PAYMENT");
        assertThat(order(otherPaid)).containsEntry("status", "PAID");
        assertThat(outboxCount("order.cancelled")).isZero();
        assertThat(outboxCount("order-item.cancelled")).isOne();
    }

    @Test
    @DisplayName("같은 이벤트를 다시 받거나 늦게 발생한 ACTIVE가 먼저 와도 결과 · 이벤트가 같다(이미 취소된 것은 건너뜀)")
    void idempotentAndOrderIndependent() {
        Long pending = place(SELLER_OPTION).orderId();
        Long ready = paid(SELLER_OPTION);
        clearOutbox();
        SellerChangedEvent suspended = sellerChanged(SELLER_ID, SellerStatus.SUSPENDED, OCCURRED_AT.plusDays(2));

        eventDelivery.deliver(suspended);
        eventDelivery.deliver(suspended);
        // 더 일찍 발생한 ACTIVE가 늦게 와도 복사본은 최신(정지)이라 되돌리지 않는다
        eventDelivery.deliver(sellerChanged(SELLER_ID, SellerStatus.ACTIVE, OCCURRED_AT.plusDays(1)));

        assertThat(order(pending)).containsEntry("status", "CANCELLED");
        assertThat(order(ready)).containsEntry("status", "CANCELLED");
        assertThat(outboxCount("order.cancelled")).isOne();
        assertThat(outboxCount("order-item.cancelled")).isOne();
    }

    @Test
    @DisplayName("판매자가 ACTIVE면(복사본도 ACTIVE) 아무것도 정리하지 않는다")
    void activeDoesNothing() {
        Long pending = place(SELLER_OPTION).orderId();
        Long ready = paid(SELLER_OPTION);
        clearOutbox();

        eventDelivery.deliver(sellerChanged(SELLER_ID, SellerStatus.ACTIVE, OCCURRED_AT.plusDays(1)));

        assertThat(order(pending)).containsEntry("status", "PENDING_PAYMENT");
        assertThat(order(ready)).containsEntry("status", "PAID");
        assertThat(outboxCount("order.cancelled") + outboxCount("order-item.cancelled")).isZero();
    }

    @Test
    @DisplayName("대상이 한 번 읽는 수(100)보다 많아도 ID 커서로 이어 읽어 모두 정리한다")
    void pagesThroughManyOrders() {
        List<Long> pendings = IntStream.range(0, SellerUnavailableCleanupService.BATCH_SIZE + 5)
            .mapToObj(i -> orderRepository.save(Order.place(BUYER_ID, List.of(new Order.RequestedItem(
                    new OrderItem.ProductSnapshot(1L, SELLER_OPTION, SELLER_ID, "상품", null, null, PRICE, 1L), true,
                    null, 1, PRICE)),
                new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
                new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now())).getId())
            .toList();

        eventDelivery.deliver(sellerChanged(SELLER_ID, SellerStatus.SUSPENDED, OCCURRED_AT.plusDays(1)));

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.orders where id in (" + String.join(",",
            pendings.stream().map(String::valueOf).toList()) + ") and status = 'CANCELLED'", Long.class))
            .isEqualTo(pendings.size());
    }

    private OrderDetail place(Long... optionIds) {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            java.util.Arrays.stream(optionIds).map(optionId -> new PlaceOrderCommand.Item(optionId, null, 1, PRICE))
                .toList(),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private Long paid(Long... optionIds) {
        OrderDetail order = place(optionIds);
        orderService.markPaid(MarkPaidCommands.matching(order, BUYER_ID, paymentId++));
        deliveryService.createFor(order.orderId());
        return order.orderId();
    }

    private Long ship(Long orderId, Long sellerId) {
        Long deliveryId = deliveryOf(orderId, sellerId).getId();
        deliveryService.ship(sellerId, deliveryId, "CJ", "1234567890");
        return deliveryId;
    }

    private Delivery deliveryOf(Long orderId, Long sellerId) {
        return deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId).stream()
            .filter(delivery -> delivery.getSellerId().equals(sellerId)).findFirst().orElseThrow();
    }

    private Delivery.Status deliveryStatus(Long orderId, Long sellerId) {
        return deliveryOf(orderId, sellerId).getStatus();
    }

    private Map<String, Object> order(Long orderId) {
        return jdbcTemplate.queryForMap("select status, cancel_reason from orders.orders where id = ?", orderId);
    }

    private List<Map<String, Object>> items(Long orderId) {
        return jdbcTemplate.queryForList("select status, cancel_reason from orders.order_item where order_id = ?",
            orderId);
    }

    private Map<Long, String> itemStatusBySeller(Long orderId) {
        Map<Long, String> statuses = new java.util.HashMap<>();
        jdbcTemplate.queryForList("select seller_id, status from orders.order_item where order_id = ?", orderId)
            .forEach(row -> statuses.put((Long) row.get("seller_id"), (String) row.get("status")));
        return statuses;
    }

    private List<String> history(Long orderId) {
        return jdbcTemplate.queryForList("select actor_type || ':' || coalesce(source, '') from "
            + "orders.order_status_history where order_id = ?", String.class, orderId);
    }

    private List<String> outboxReasons(String eventType, Long orderId) {
        return jdbcTemplate.queryForList("select payload::jsonb -> 'data' ->> 'reason' from orders.order_outbox "
            + "where event_type = ? and payload::jsonb -> 'data' ->> 'orderId' = ?", String.class, eventType,
            String.valueOf(orderId));
    }

    private long outboxCount(String eventType) {
        return jdbcTemplate.queryForObject("select count(*) from orders.order_outbox where event_type = ?", Long.class,
            eventType);
    }

    private void clearOutbox() {
        jdbcTemplate.update("delete from orders.order_outbox");
    }

    private static ReplicaProduct.Values product(Long optionId, Long sellerId) {
        return new ReplicaProduct.Values(optionId, optionId, sellerId, "상품", "네이비 / 110", "110", PRICE, 1L, "ON_SALE",
            "ON_SALE", false, OCCURRED_AT);
    }

    private static SellerChangedEvent sellerChanged(Long sellerId, SellerStatus status, LocalDateTime occurredAt) {
        return new SellerChangedEvent(UUID.randomUUID(), occurredAt,
            new SellerChangedEvent.Data(sellerId, "키들리샵 " + sellerId, status));
    }
}
