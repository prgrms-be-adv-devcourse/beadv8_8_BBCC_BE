package com.bbcc.kidly.boundedcontext.order.app.order;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.shared.order.event.OrderPaidEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제완료 이벤트(order.paid, #899): 결제대기 주문이 결제완료로 실제로 바뀔 때만 같은 트랜잭션에서 주문 아웃박스(키 = 주문 ID)에 한 행.
 * 상품 칸은 주문할 때의 스냅샷이고 배송지 · 자녀 칸은 없다. 관리자 결제 복구 경로 · 배송 생성 실패 롤백은 PaymentRecoveryIntegrationTest가 본다.
 */
@IntegrationTest
class OrderPaidEventIntegrationTest {

    private static final Long BUYER_ID = 6_200L;
    private static final Long OTHER_SELLER_ID = 3L;
    private static final long PRICE_A = 25_000L;
    private static final long PRICE_B = 30_000L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderQueryService orderQueries;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(PRICE_A, OCCURRED_AT)));
        // 옵션명을 저장하지 않던 옛 행처럼 옵션명이 없는 상품(다른 판매자)
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", PRICE_B, 2L, "ON_SALE", null, false, OCCURRED_AT)));
        recorder.clear();
    }

    @Test
    @DisplayName("결제 완료로 결제완료가 되면 order.paid 한 건: 주문 · 회원 · 결제 시각 · 총액 · 주문상품 스냅샷(옵션명 없으면 null) · 키 = 주문 ID")
    void publishesOnce() {
        OrderDetail order = place();

        pay(order);

        Order stored = orderRepository.findById(order.orderId()).orElseThrow();
        List<OrderDetail.Item> items = order.items();
        assertThat(recorder.events(OrderPaidEvent.class)).singleElement().satisfies(event -> {
            // 시각은 메모리 값(나노초까지)이고 DB 값은 드라이버가 마이크로초로 반올림한 값이라 1µs 안인지 보고, 나머지 칸은 그대로 본다
            assertThat(event.data()).isEqualTo(new OrderPaidEvent.Data(order.orderId(), BUYER_ID, event.data().paidAt(),
                PRICE_A * 2 + PRICE_B, List.of(
                    new OrderPaidEvent.Item(items.get(0).orderItemId(), SELLER_ID, 1L, "상품 A", "네이비 / 110", 2,
                        PRICE_A * 2),
                    new OrderPaidEvent.Item(items.get(1).orderItemId(), OTHER_SELLER_ID, 2L, "상품 B", null, 1,
                        PRICE_B))));
            assertThat(stored.getPaidAt()).isCloseTo(event.data().paidAt(), within(1, ChronoUnit.MICROS));
            assertThat(stored.getPaidAt()).isCloseTo(event.occurredAt(), within(1, ChronoUnit.MICROS));
        });
        List<Map<String, Object>> rows = paidRows();
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("message_key", String.valueOf(order.orderId()))
                .containsEntry("topic", "kidly.order.events");
            JsonNode data = jsonMapper.readTree((String) row.get("payload")).get("data");
            assertThat(data.propertyNames()).containsExactly("orderId", "memberId", "paidAt", "totalAmount", "items");
            assertThat(data.get("items").get(0).propertyNames()).containsExactly("orderItemId", "sellerId",
                "productId", "productName", "optionName", "quantity", "amount");
        });
    }

    @Test
    @DisplayName("같은 결제 완료가 다시 와도(이미 결제완료) order.paid는 더 나가지 않는다")
    void notAgainWhenAlreadyPaid() {
        OrderDetail order = place();
        PaymentCompletedEvent completed = pay(order);

        eventDelivery.deliver(completed);

        assertThat(recorder.events(OrderPaidEvent.class)).hasSize(1);
        assertThat(paidRows()).hasSize(1);
    }

    @Test
    @DisplayName("결제 전에 취소된 주문에 결제 완료가 와도 · 결제가 실패해도 order.paid는 나가지 않는다")
    void notWhenCancelledOrFailed() {
        OrderDetail cancelled = place();
        eventDelivery.deliver(stockDeducted(cancelled.orderId()));
        orderService.cancelByBuyer(BUYER_ID, cancelled.orderId());
        eventDelivery.deliver(PaymentEvents.completed(cancelled, BUYER_ID));
        OrderDetail failed = place();
        eventDelivery.deliver(stockDeducted(failed.orderId()));
        eventDelivery.deliver(PaymentEvents.insufficientBalance(orderQueries.getOrder(BUYER_ID, failed.orderId()),
            BUYER_ID));

        assertThat(recorder.events(OrderPaidEvent.class)).isEmpty();
        assertThat(paidRows()).isEmpty();
    }

    private OrderDetail place() {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 2, PRICE_A), new PlaceOrderCommand.Item(2L, null, 1, PRICE_B)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시")));
    }

    private PaymentCompletedEvent pay(OrderDetail order) {
        eventDelivery.deliver(stockDeducted(order.orderId()));
        PaymentCompletedEvent completed = PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, order.orderId()),
            BUYER_ID);
        eventDelivery.deliver(completed);
        return completed;
    }

    private static StockDeductedEvent stockDeducted(Long orderId) {
        return new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(), new StockDeductedEvent.Data(orderId));
    }

    private List<Map<String, Object>> paidRows() {
        return jdbcTemplate.queryForList("select * from orders.order_outbox where event_type = 'order.paid'");
    }
}
