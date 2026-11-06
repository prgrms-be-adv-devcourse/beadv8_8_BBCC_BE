package com.bbcc.kidly.boundedcontext.cart.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.cart.CartOwners;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderQueryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.CartItemPurchasedEvent;
import com.bbcc.kidly.shared.order.event.CartItemRemovedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 장바구니 빼기 이벤트(cart.item-removed, #709)가 사용자가 줄을 뺄 때만 빼기와 같은 트랜잭션에서 주문 아웃박스(kidly.order.events,
 * 키 = 회원 ID)에 한 행 저장되는지. 수량 변경 · 실패 · 롤백은 행이 없다. 결제완료 정리(산 것)는 cart.item-purchased 한 행이다(#998).
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class CartItemRemovedOutboxIntegrationTest {

    private static final Long OPTION_ID = 1L;
    private static final Long CHILD_ID = 1L;
    private static final Long PRODUCT_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 3L;
    private static final String REMOVED = "cart.item-removed";

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderQueryService orderQueries;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        recorder.clear();
    }

    @Test
    @DisplayName("줄을 빼면 cart.item-removed 한 행: 키 = 회원 ID, 본문 7칸 + data(뺄 때 줄 수량 · 자녀 · 상품), 보내지 않은 상태")
    void savesOneRow() {
        Long cartItemId = cartService.addItem(CartOwners.member(BUYER_ID), OPTION_ID, CHILD_ID, 2).line().cartItemId();
        cartService.addItem(CartOwners.member(BUYER_ID), OPTION_ID, CHILD_ID, 3);

        cartService.removeItem(CartOwners.member(BUYER_ID), cartItemId);

        CartItemRemovedEvent event = recorder.events(CartItemRemovedEvent.class).getFirst();
        assertThat(event.data()).isEqualTo(
            new CartItemRemovedEvent.Data(cartItemId, BUYER_ID, CHILD_ID, PRODUCT_ID, OPTION_ID, 5, "MEMBER",
                BUYER_ID));
        List<Map<String, Object>> rows = rows();
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("event_id", event.eventId())
                .containsEntry("topic", "kidly.order.events")
                .containsEntry("message_key", String.valueOf(BUYER_ID))
                .containsEntry("sent_at", null);
            JsonNode payload = jsonMapper.readTree((String) row.get("payload"));
            assertThat(payload.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion",
                "occurredAt", "producer", "aggregateId", "correlationId", "data");
            assertThat(payload.get("eventType").asString()).isEqualTo(REMOVED);
            assertThat(payload.get("producer").asString()).isEqualTo("order");
            assertThat(payload.get("aggregateId").asString()).isEqualTo(String.valueOf(cartItemId));
            assertThat(payload.get("correlationId").asString()).isEqualTo(String.valueOf(BUYER_ID));
            assertThat(payload.get("data").toString()).isEqualTo(jsonMapper.writeValueAsString(event.data()));
            // 원인 · 주문 · 예약 칸은 없다(결제 정리는 cart.item-purchased, #998)
            assertThat(payload.get("data").propertyNames()).containsExactly("cartItemId", "memberId", "childId",
                "productId", "optionId", "quantity", "ownerType", "ownerId");
        });
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item", Long.class)).isZero();
    }

    @Test
    @DisplayName("없는 줄 · 남의 줄을 빼면 실패하고 행이 없다. 수량 변경도 행이 없다")
    void nothingWhenFailsOrQuantityChanged() {
        Long cartItemId = cartService.addItem(CartOwners.member(BUYER_ID), OPTION_ID, null, 1).line().cartItemId();

        assertThatThrownBy(() -> cartService.removeItem(CartOwners.member(BUYER_ID), cartItemId + 100))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> cartService.removeItem(CartOwners.member(OTHER_MEMBER_ID), cartItemId))
            .isInstanceOf(BusinessException.class);
        cartService.changeQuantity(CartOwners.member(BUYER_ID), cartItemId, 4);

        assertThat(rows()).isEmpty();
        assertThat(recorder.events(CartItemRemovedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("결제가 끝나 장바구니가 정리되면(산 것) 줄이 지워지고 cart.item-purchased 한 행(키 = 회원 ID), cart.item-removed는 없다(#998)")
    void purchasedWhenClearedByPayment() {
        Long cartItemId = cartService.addItem(CartOwners.member(BUYER_ID), OPTION_ID, CHILD_ID, 2).line().cartItemId();
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(OPTION_ID, CHILD_ID, 2, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시")));

        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(order.orderId())));
        eventDelivery.deliver(PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, order.orderId()), BUYER_ID));

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item", Long.class))
            .as("결제완료 정리로 줄은 지워진다").isZero();
        Long orderItemId = order.items().getFirst().orderItemId();
        CartItemPurchasedEvent event = recorder.events(CartItemPurchasedEvent.class).getFirst();
        assertThat(recorder.events(CartItemPurchasedEvent.class)).hasSize(1);
        assertThat(event.data()).isEqualTo(new CartItemPurchasedEvent.Data(cartItemId, "MEMBER", BUYER_ID, BUYER_ID,
            OPTION_ID, CHILD_ID, 2, order.orderId(), orderItemId, null));
        assertThat(recorder.events(CartItemRemovedEvent.class)).isEmpty();
        assertThat(rows()).isEmpty();
        List<Map<String, Object>> purchased = jdbcTemplate.queryForList(
            "select * from orders.order_outbox where event_type = 'cart.item-purchased' order by id");
        assertThat(purchased).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("event_id", event.eventId())
                .containsEntry("topic", "kidly.order.events")
                .containsEntry("message_key", String.valueOf(BUYER_ID));
            JsonNode payload = jsonMapper.readTree((String) row.get("payload"));
            assertThat(payload.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion",
                "occurredAt", "producer", "aggregateId", "correlationId", "data");
            assertThat(payload.get("aggregateId").asString()).isEqualTo(String.valueOf(cartItemId));
            assertThat(payload.get("data").toString()).isEqualTo(jsonMapper.writeValueAsString(event.data()));
        });
    }

    @Test
    @DisplayName("빼기를 감싼 트랜잭션이 롤백되면 줄은 남고 아웃박스 행도 Spring 이벤트도 없다")
    void rollsBackWithBusiness() {
        Long cartItemId = cartService.addItem(CartOwners.member(BUYER_ID), OPTION_ID, null, 1).line().cartItemId();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            cartService.removeItem(CartOwners.member(BUYER_ID), cartItemId);
            throw new IllegalStateException("업무 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item", Long.class)).isOne();
        assertThat(rows()).isEmpty();
        assertThat(recorder.events(CartItemRemovedEvent.class)).isEmpty();
    }

    private List<Map<String, Object>> rows() {
        return jdbcTemplate.queryForList("select * from orders.order_outbox where event_type = ? order by id",
            REMOVED);
    }
}
