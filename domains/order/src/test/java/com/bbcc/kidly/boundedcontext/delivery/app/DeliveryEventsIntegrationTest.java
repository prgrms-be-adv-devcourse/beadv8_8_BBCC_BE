package com.bbcc.kidly.boundedcontext.delivery.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import ch.qos.logback.classic.Level;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.CapturedLogs;
import com.bbcc.kidly.boundedcontext.order.MarkPaidCommands;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.DeliveryDeliveredEvent;
import com.bbcc.kidly.shared.order.event.DeliveryShippedEvent;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 배송 출발 · 완료 이벤트(delivery.shipped · delivery.delivered, #900): 발송 · 처음 완료(판매자 수동 · 택배 조회)에 한 건씩,
 * 같은 트랜잭션에서 주문 아웃박스(키 = 주문 ID)로 나간다. 이미 완료 · 실패 · 주문 없음은 나가지 않는다.
 */
@IntegrationTest
class DeliveryEventsIntegrationTest {

    private static final Long BUYER_ID = 6_300L;
    private static final String TRACKING_NUMBER = "1234567890";

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Order.Policy orderPolicy;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        recorder.clear();
    }

    @Test
    @DisplayName("발송하면 delivery.shipped 한 건: 배송 · 주문 · 구매자 · 판매자 · 택배사 코드와 이름 · 운송장 번호 · 발송 시각, 키 = 주문 ID")
    void shipped() {
        Delivery delivery = deliveryOf(paidOrder());

        deliveryService.ship(SELLER_ID, delivery.getId(), "CJ", TRACKING_NUMBER);

        Delivery shipped = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(recorder.events(DeliveryShippedEvent.class)).singleElement().satisfies(event -> {
            // 이벤트 시각은 메모리 값(나노초까지), DB 값은 드라이버가 마이크로초로 반올림한 값이라 1µs 안인지 보고 나머지 칸은 그대로 본다
            assertThat(event.data()).isEqualTo(new DeliveryShippedEvent.Data(delivery.getId(), delivery.getOrderId(),
                BUYER_ID, SELLER_ID, "CJ", "CJ대한통운", TRACKING_NUMBER, event.data().shippedAt()));
            assertThat(shipped.getShippedAt()).isCloseTo(event.data().shippedAt(), within(1, ChronoUnit.MICROS));
            assertThat(event.correlationId()).isEqualTo(String.valueOf(delivery.getOrderId()));
        });
        assertThat(rows("delivery.shipped")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("message_key", String.valueOf(delivery.getOrderId()))
                .containsEntry("topic", "kidly.order.events");
            assertThat((String) row.get("payload")).doesNotContain("홍길동", "010-0000-0000", "서울시");
        });
    }

    @Test
    @DisplayName("판매자 수동 완료면 delivery.delivered 한 건(완료 시각 = 배송 완료 시각, 주문상품 · 자동 구매확정 예정 시각 = 주문상품 "
        + "배송 완료 시각 + 기간), 다시 완료해도 · 뒤에 택배 조회 완료가 와도 더 없다")
    void deliveredByHandOnce() {
        Delivery delivery = shippedDelivery("DUMMY");

        deliveryService.complete(SELLER_ID, delivery.getId());
        deliveryService.complete(SELLER_ID, delivery.getId());
        deliveryService.completeByTracking(delivery.getId(), LocalDateTime.now().plusMinutes(1));

        Delivery delivered = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(recorder.events(DeliveryDeliveredEvent.class)).singleElement().satisfies(event -> {
            // 시각은 DB 값(드라이버가 마이크로초로 반올림)이 메모리 값과 1µs 안인지 본다
            Map<String, Object> item = orderItem(delivery.getOrderId());
            assertThat(event.data()).isEqualTo(new DeliveryDeliveredEvent.Data(delivery.getId(),
                delivery.getOrderId(), BUYER_ID, SELLER_ID, "DUMMY", "테스트택배", TRACKING_NUMBER,
                event.data().deliveredAt(), event.data().autoConfirmAt(), List.of(new DeliveryDeliveredEvent.Item(
                    (Long) item.get("id"), (Long) item.get("product_id"), 1))));
            assertThat(delivered.getDeliveredAt()).isCloseTo(event.data().deliveredAt(), within(1, ChronoUnit.MICROS));
            // 주문상품 배송 완료 시각은 배송 완료 시각과 같은 값이고, 예정 시각은 그 값 + 자동 구매확정 기간(자동 확정과 같은 기준)
            LocalDateTime itemDeliveredAt = ((java.sql.Timestamp) item.get("delivered_at")).toLocalDateTime();
            assertThat(itemDeliveredAt).isEqualTo(delivered.getDeliveredAt());
            assertThat(event.data().autoConfirmAt()).isCloseTo(itemDeliveredAt.plus(orderPolicy.autoConfirmAfter()),
                within(1, ChronoUnit.MICROS));
        });
        assertThat(rows("delivery.delivered")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("message_key", String.valueOf(delivery.getOrderId()));
            assertThat((String) row.get("payload")).contains("\"autoConfirmAt\"", "\"items\"", "\"orderItemId\"");
        });
    }

    @Test
    @DisplayName("택배 조회 완료면 delivery.delivered 한 건(완료 시각 = 택배사 시각), 뒤에 수동 완료가 와도 더 없다")
    void deliveredByTrackingOnce() {
        Delivery delivery = shippedDelivery("HANJIN");
        LocalDateTime trackedAt = LocalDateTime.now().plusMinutes(1).withNano(0);

        assertThat(deliveryService.completeByTracking(delivery.getId(), trackedAt)).isTrue();
        assertThat(deliveryService.completeByTracking(delivery.getId(), trackedAt.plusMinutes(1))).isFalse();
        deliveryService.complete(SELLER_ID, delivery.getId());

        assertThat(recorder.events(DeliveryDeliveredEvent.class)).singleElement()
            .satisfies(event -> {
                assertThat(event.data().deliveredAt()).isEqualTo(trackedAt);
                assertThat(event.data().carrier()).isEqualTo("HANJIN");
                assertThat(event.data().carrierName()).isEqualTo("한진택배");
                assertThat(event.data().memberId()).isEqualTo(BUYER_ID);
                assertThat(event.data().autoConfirmAt()).isEqualTo(trackedAt.plus(orderPolicy.autoConfirmAfter()));
                assertThat(event.data().items()).singleElement()
                    .satisfies(item -> assertThat(item.orderItemId())
                        .isEqualTo(orderItem(delivery.getOrderId()).get("id")));
            });
        assertThat(rows("delivery.delivered")).hasSize(1);
    }

    @Test
    @DisplayName("발송 · 완료가 실패하면(이미 발송 409 · 발송 전 완료 409 · 남의 배송 404) 배송 이벤트가 없다")
    void nothingWhenFails() {
        Delivery notShipped = deliveryOf(paidOrder());
        recorder.clear();
        jdbcTemplate.update("delete from orders.order_outbox");

        assertThatThrownBy(() -> deliveryService.complete(SELLER_ID, notShipped.getId()))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> deliveryService.ship(SELLER_ID + 1, notShipped.getId(), "CJ", TRACKING_NUMBER))
            .isInstanceOf(BusinessException.class);

        assertThat(recorder.events(DomainEvent.class)).as("발송 전 완료 · 남의 배송 발송").isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox", Long.class)).isZero();
        Delivery shipped = shippedDelivery("CJ");
        recorder.clear();
        jdbcTemplate.update("delete from orders.order_outbox");

        assertThatThrownBy(() -> deliveryService.ship(SELLER_ID, shipped.getId(), "CJ", TRACKING_NUMBER))
            .isInstanceOf(BusinessException.class);

        assertThat(recorder.events(DomainEvent.class)).isEmpty();
        assertThat(rows("delivery.shipped")).isEmpty();
        assertThat(rows("delivery.delivered")).isEmpty();
    }

    @Test
    @DisplayName("배송의 주문이 없으면(계약 밖) 상태는 바뀌고 이벤트는 내지 않으며 WARN 한 줄(주문 · 배송 ID · 종류만)을 남긴다")
    void nothingWithoutOrder() {
        Delivery orphan = deliveryRepository.save(Delivery.create(987_654L, SELLER_ID));

        try (CapturedLogs logs = CapturedLogs.start()) {
            deliveryService.ship(SELLER_ID, orphan.getId(), "CJ", TRACKING_NUMBER);

            assertThat(logs.events()).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(event -> event.getFormattedMessage())
                .containsExactly("배송 이벤트 건너뜀: orderId=987654 deliveryId=" + orphan.getId()
                    + " eventType=delivery.shipped reason=order-not-found");
        }

        assertThat(deliveryRepository.findById(orphan.getId()).orElseThrow().getStatus())
            .isEqualTo(Delivery.Status.SHIPPING);
        assertThat(recorder.events(DeliveryShippedEvent.class)).isEmpty();
        assertThat(rows("delivery.shipped")).isEmpty();
    }

    private OrderDetail paidOrder() {
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        orderService.markPaid(MarkPaidCommands.matching(order, BUYER_ID, 500L));
        deliveryService.createFor(order.orderId());
        return order;
    }

    private Delivery shippedDelivery(String carrier) {
        Delivery delivery = deliveryOf(paidOrder());
        deliveryService.ship(SELLER_ID, delivery.getId(), carrier, TRACKING_NUMBER);
        return deliveryRepository.findById(delivery.getId()).orElseThrow();
    }

    private Delivery deliveryOf(OrderDetail order) {
        return deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId()).getFirst();
    }

    private Map<String, Object> orderItem(Long orderId) {
        return jdbcTemplate.queryForMap("select id, product_id, delivered_at from orders.order_item where order_id = ?",
            orderId);
    }

    private List<Map<String, Object>> rows(String eventType) {
        return jdbcTemplate.queryForList("select * from orders.order_outbox where event_type = ? order by id",
            eventType);
    }
}
