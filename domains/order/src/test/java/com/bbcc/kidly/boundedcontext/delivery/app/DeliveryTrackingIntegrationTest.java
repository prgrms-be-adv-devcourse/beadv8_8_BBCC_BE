package com.bbcc.kidly.boundedcontext.delivery.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.delivery.app.dto.TrackingTarget;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.MarkPaidCommands;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.AutoConfirmTarget;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 택배 조회로 배송 완료가 DB에 한 번만 반영되는지 본다(배송 · 주문상품 deliveredAt · 상태 이력 · 자동 구매확정).
 * 테스트 프로필은 인증값이 비어 실제 API를 부르지 않는다. 조회 결과는 DeliveryService를 직접 불러 대신한다.
 */
@IntegrationTest
class DeliveryTrackingIntegrationTest {

    private static final Long BUYER_ID = 6_100L;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private DeliveryTrackingService trackingService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @Test
    @DisplayName("테스트 프로필은 인증값이 비어 택배 조회가 비활성이다")
    void disabledInTestProfile() {
        assertThat(trackingService.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("택배 완료가 두 번 와도 배송 완료 · 주문상품 deliveredAt · 상태 이력은 한 번, 시각은 처음 것이다")
    void completesOnce() {
        OrderDetail order = paidOrder();
        Delivery shipped = ship(order.orderId(), "2026-11-01T09:00:00Z");
        LocalDateTime first = shipped.getShippedAt().plusHours(1);

        boolean firstResult = deliveryService.completeByTracking(shipped.getId(), first);
        boolean secondResult = deliveryService.completeByTracking(shipped.getId(), first.plusHours(1));

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isFalse();
        Delivery delivery = deliveryRepository.findById(shipped.getId()).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isEqualTo(first);
        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).items())
            .singleElement().satisfies(item -> assertThat(item.deliveredAt()).isEqualTo(first));
        assertThat(deliveredHistories(order.orderId()))
            .singleElement().satisfies(history -> {
                assertThat(history.getActorType()).isEqualTo(OrderStatusHistory.ActorType.SYSTEM);
                assertThat(history.getSource()).isEqualTo("scheduler:delivery-tracking");
            });
    }

    @Test
    @DisplayName("택배 완료 뒤 판매자 수동 완료가 와도 배송 완료 이력은 한 번이고 시각은 택배사 시각이다")
    void manualAfterTracking() {
        OrderDetail order = paidOrder();
        Delivery shipped = ship(order.orderId(), "2026-11-01T09:00:00Z");
        LocalDateTime trackedAt = shipped.getShippedAt().plusHours(1);

        deliveryService.completeByTracking(shipped.getId(), trackedAt);
        deliveryService.complete(SELLER_ID, shipped.getId());

        assertThat(deliveryRepository.findById(shipped.getId()).orElseThrow().getDeliveredAt()).isEqualTo(trackedAt);
        assertThat(deliveredHistories(order.orderId())).hasSize(1);
    }

    @Test
    @DisplayName("판매자 수동 완료 뒤 택배 완료가 와도 무시되고 이력은 한 번이다")
    void trackingAfterManual() {
        OrderDetail order = paidOrder();
        Delivery shipped = ship(order.orderId(), "2026-11-01T09:00:00Z");

        deliveryService.complete(SELLER_ID, shipped.getId());
        boolean result = deliveryService.completeByTracking(shipped.getId(), shipped.getShippedAt().plusHours(1));

        assertThat(result).isFalse();
        assertThat(deliveredHistories(order.orderId())).singleElement()
            .satisfies(history -> assertThat(history.getActorType()).isEqualTo(OrderStatusHistory.ActorType.SELLER));
    }

    @Test
    @DisplayName("택배사 시각이 발송 시각보다 앞이면(재사용 운송장) 배송 중 그대로이고 주문상품 기록 · 이력도 없다")
    void ignoresOldTrackedTime() {
        OrderDetail order = paidOrder();
        Delivery shipped = ship(order.orderId(), "000000000000");

        boolean result = deliveryService.completeByTracking(shipped.getId(), shipped.getShippedAt().minusDays(100));

        assertThat(result).isFalse();
        assertThat(deliveryRepository.findById(shipped.getId()).orElseThrow().getStatus())
            .isEqualTo(Delivery.Status.SHIPPING);
        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).items())
            .singleElement().satisfies(item -> assertThat(item.deliveredAt()).isNull());
        assertThat(deliveredHistories(order.orderId())).isEmpty();
    }

    @Test
    @DisplayName("조회 대상은 발송 중이고 조회를 멈추지 않은 배송뿐이며, 조회 중지된 배송도 수동 완료는 된다")
    void targetsAndStop() {
        Delivery stopped = ship(paidOrder().orderId(), "test");
        Delivery tracking = ship(paidOrder().orderId(), "2026-11-01T09:00:00Z");
        Delivery ready = deliveryOf(paidOrder().orderId());

        assertThat(deliveryService.stopTracking(stopped.getId(), "BAD_REQUEST")).isTrue();
        assertThat(deliveryService.stopTracking(stopped.getId(), "BAD_REQUEST")).isFalse();

        assertThat(deliveryService.findTrackingTargets(0L, 100)).extracting(TrackingTarget::deliveryId)
            .contains(tracking.getId())
            .doesNotContain(stopped.getId(), ready.getId());
        deliveryService.complete(SELLER_ID, stopped.getId());
        assertThat(deliveryRepository.findById(stopped.getId()).orElseThrow().getStatus())
            .isEqualTo(Delivery.Status.DELIVERED);
    }

    @Test
    @DisplayName("택배 완료로 배송 완료된 주문상품은 자동 구매확정을 두 번 불러도 한 번만 확정된다")
    void autoConfirmOnce() {
        OrderDetail order = paidOrder();
        Delivery shipped = ship(order.orderId(), "2026-11-01T09:00:00Z");
        deliveryService.completeByTracking(shipped.getId(), shipped.getShippedAt().plusSeconds(1));
        // 자동 구매확정은 실제 현재 시각으로 기간(기본 15일)을 다시 보므로, 테스트 준비로 주문상품 배송 완료 시각만 16일 전으로 옮긴다
        jdbcTemplate.update(
            "update orders.order_item set delivered_at = delivered_at - interval '16 days' where order_id = ?",
            order.orderId());

        List<AutoConfirmTarget> targets = orderService.findAutoConfirmTargets(LocalDateTime.now(), 0L, 100);
        targets.forEach(target -> orderService.confirmAutomatically(target.orderId(), target.orderItemId()));
        targets.forEach(target -> orderService.confirmAutomatically(target.orderId(), target.orderItemId()));

        assertThat(targets).extracting(AutoConfirmTarget::orderId).contains(order.orderId());
        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).items())
            .singleElement().satisfies(item -> {
                assertThat(item.status()).isEqualTo("CONFIRMED");
                assertThat(item.confirmType()).isEqualTo("AUTO");
            });
        assertThat(historyRepository.findAllByOrderIdOrderByOccurredAtAscIdAsc(order.orderId()))
            .filteredOn(history -> "CONFIRMED".equals(history.getToStatus()))
            .hasSize(1);
    }

    private OrderDetail paidOrder() {
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        orderService.markPaid(MarkPaidCommands.matching(order, BUYER_ID, 500L));
        deliveryService.createFor(order.orderId());
        return order;
    }

    private Delivery ship(Long orderId, String trackingNumber) {
        Long deliveryId = deliveryOf(orderId).getId();
        deliveryService.ship(SELLER_ID, deliveryId, "DUMMY", trackingNumber);
        return deliveryRepository.findById(deliveryId).orElseThrow();
    }

    private Delivery deliveryOf(Long orderId) {
        return deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId).getFirst();
    }

    private List<OrderStatusHistory> deliveredHistories(Long orderId) {
        return historyRepository.findAllByOrderIdOrderByOccurredAtAscIdAsc(orderId).stream()
            .filter(history -> history.getTargetType() == OrderStatusHistory.TargetType.DELIVERY)
            .filter(history -> "DELIVERED".equals(history.getToStatus()))
            .toList();
    }
}
