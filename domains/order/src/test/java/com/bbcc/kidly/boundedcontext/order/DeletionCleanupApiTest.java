package com.bbcc.kidly.boundedcontext.order;

import static com.bbcc.kidly.boundedcontext.cart.in.web.CartController.CART_ITEMS_PATH;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.wish.in.web.WishRequestController.WISH_REQUESTS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderQueryService;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 자녀 · 자녀 계정 삭제와 회원 탈퇴 정리(#1001): 삭제 · 탈퇴 이벤트를 받으면 주문 서비스의 장바구니 · 조르기 · 결제대기 주문을 정리한다.
 * 정리로 지운 칸은 cart.item-removed를 내지 않고, 같은 이벤트가 여러 번 · 순서가 바뀌어 와도 결과가 같다.
 * 자녀 1(계정 100)의 보호자는 BUYER_ID. 이벤트는 수신 처리 메서드로 넣는다(Kafka로 받는 것과 같은 처리, OrderEventDelivery).
 */
@FunctionalTest
class DeletionCleanupApiTest {

    private static final Long CHILD_ID = 1L;
    private static final Long ACCOUNT_ID = 100L;
    private static final long PRICE = 25_000L;
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderQueryService orderQueries;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(PRICE, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.product(2L, 12_000L, "ON_SALE", false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        eventDelivery.deliver(account(false, OCCURRED_AT));
    }

    @Test
    @DisplayName("자녀 삭제: 그 자녀 장바구니 칸(예약 칸 포함)을 지우고 진행 중 조르기를 CHILD_DELETED로 철회(wish.cancelled 1건)한다. "
        + "cart.item-removed는 없고, 보호자 칸 · 결제된 주문은 그대로. 다시 와도 같다")
    void childDeleted() {
        String reserved = addToCart(child(), "1");
        String plain = addToCart(child(), "2");
        String parentLine = addToCart(TestMembers.buyer(BUYER_ID), "1");
        String requestId = sendWish(reserved);
        Long paidOrder = order(Map.of("optionId", "2", "childId", "1", "quantity", 1, "unitPrice", 12_000));
        pay(paidOrder);
        jdbcTemplate.update("delete from orders.order_outbox");

        eventDelivery.deliver(childDeleted(OCCURRED_AT.plusDays(1)));
        eventDelivery.deliver(childDeleted(OCCURRED_AT.plusDays(1)));

        assertThat(cartItemIds()).containsExactly(Long.valueOf(parentLine));
        assertThat(Long.valueOf(reserved)).isNotIn(cartItemIds());
        assertThat(Long.valueOf(plain)).isNotIn(cartItemIds());
        assertThat(wishRow(requestId)).containsEntry("status", "CANCELLED")
            .containsEntry("cancel_reason", "CHILD_DELETED");
        assertThat(count("orders.cart_reservation")).isZero();
        assertThat(eventTypes()).containsExactly("wish.cancelled");
        assertThat(jsonMapper.readTree(outboxPayload("wish.cancelled")).get("data").get("cancelReason").asString())
            .isEqualTo("CHILD_DELETED");
        assertThat(orderQueries.getOrder(BUYER_ID, paidOrder).status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("자녀 삭제가 순서가 바뀌어 와도(삭제 뒤 예전 프로필) 결과가 같고, 예전 프로필이 장바구니를 되살리지 않는다")
    void childDeletedOutOfOrder() {
        String line = addToCart(child(), "1");
        String requestId = sendWish(line);
        jdbcTemplate.update("delete from orders.order_outbox");

        eventDelivery.deliver(childDeleted(OCCURRED_AT.plusDays(2)));
        eventDelivery.deliver(childActive(OCCURRED_AT.plusDays(1)));
        eventDelivery.deliver(childDeleted(OCCURRED_AT.plusDays(1)));

        assertThat(cartItemIds()).isEmpty();
        assertThat(wishRow(requestId)).containsEntry("status", "CANCELLED")
            .containsEntry("cancel_reason", "CHILD_DELETED");
        assertThat(eventTypes()).containsExactly("wish.cancelled");
        assertThat(jdbcTemplate.queryForObject("select is_deleted from orders.replica_child where child_id = ?",
            Boolean.class, CHILD_ID)).isTrue();
    }

    @Test
    @DisplayName("자녀 계정 삭제: 그 계정의 프로필 장바구니를 지우고 진행 중 조르기를 ACCOUNT_DELETED로 철회한다. 다시 와도 · 뒤이어 자녀 삭제가 "
        + "와도 더 철회하지 않는다(먼저 온 원인이 남는다)")
    void childAccountDeleted() {
        String line = addToCart(child(), "1");
        String requestId = sendWish(line);
        String parentLine = addToCart(TestMembers.buyer(BUYER_ID), "1");
        jdbcTemplate.update("delete from orders.order_outbox");

        eventDelivery.deliver(account(true, OCCURRED_AT.plusDays(1)));
        eventDelivery.deliver(account(true, OCCURRED_AT.plusDays(1)));
        eventDelivery.deliver(account(false, OCCURRED_AT));
        eventDelivery.deliver(childDeleted(OCCURRED_AT.plusDays(2)));

        assertThat(cartItemIds()).containsExactly(Long.valueOf(parentLine));
        assertThat(wishRow(requestId)).containsEntry("status", "CANCELLED")
            .containsEntry("cancel_reason", "ACCOUNT_DELETED");
        assertThat(eventTypes()).containsExactly("wish.cancelled");
        client.get().uri(WISH_REQUESTS_PATH).headers(child()).exchange().expectStatus().isForbidden();
    }

    @Test
    @DisplayName("회원 탈퇴: 결제대기 주문을 취소(order.cancelled 1건, 이력 주체 SYSTEM · member.withdrawn)하고 회원 장바구니를 비운다. "
        + "결제된 주문 · 자녀 장바구니는 그대로이고 cart.item-removed는 없다. 다시 와도 같다")
    void memberWithdrawn() {
        String memberLine = addToCart(TestMembers.buyer(BUYER_ID), "1");
        String childLine = addToCart(child(), "2");
        Long pendingOrder = order(Map.of("optionId", "1", "quantity", 1, "unitPrice", PRICE));
        Long paidOrder = order(Map.of("optionId", "2", "quantity", 1, "unitPrice", 12_000));
        pay(paidOrder);
        jdbcTemplate.update("delete from orders.order_outbox");

        eventDelivery.deliver(withdrawn());
        eventDelivery.deliver(withdrawn());

        assertThat(orderQueries.getOrder(BUYER_ID, pendingOrder).status()).isEqualTo("CANCELLED");
        assertThat(orderQueries.getOrder(BUYER_ID, paidOrder).status()).isEqualTo("PAID");
        assertThat(eventTypes()).containsExactly("order.cancelled");
        assertThat(jdbcTemplate.queryForMap("select actor_type, source from orders.order_status_history "
            + "where order_id = ? and to_status = 'CANCELLED'", pendingOrder))
            .containsEntry("actor_type", "SYSTEM").containsEntry("source", "member.withdrawn");
        assertThat(cartItemIds()).containsExactly(Long.valueOf(childLine));
        assertThat(Long.valueOf(memberLine)).isNotIn(cartItemIds());
    }

    private List<Long> cartItemIds() {
        return jdbcTemplate.queryForList("select id from orders.cart_item order by id", Long.class);
    }

    private Map<String, Object> wishRow(String requestId) {
        return jdbcTemplate.queryForMap("select status, cancel_reason from orders.wish_request where id = ?",
            Long.valueOf(requestId));
    }

    private List<String> eventTypes() {
        return jdbcTemplate.queryForList("select event_type from orders.order_outbox order by id", String.class);
    }

    private String outboxPayload(String eventType) {
        return jdbcTemplate.queryForObject("select payload from orders.order_outbox where event_type = ?",
            String.class, eventType);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private void pay(Long orderId) {
        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(orderId)));
        eventDelivery.deliver(PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, orderId), BUYER_ID));
    }

    private Long order(Map<String, Object> line) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(line));
        body.put("shippingAddress", ADDRESS);
        URI location = client.post().uri(ORDERS_PATH).headers(TestMembers.buyer(BUYER_ID)).body(body).exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return Long.valueOf(location.getPath().substring((ORDERS_PATH + "/").length()));
    }

    private String sendWish(String cartItemId) {
        URI location = client.post().uri(WISH_REQUESTS_PATH).headers(child())
            .body(Map.of("cartItemIds", List.of(cartItemId))).exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((WISH_REQUESTS_PATH + "/").length());
    }

    private String addToCart(Consumer<HttpHeaders> headers, String optionId) {
        URI location = client.post().uri(CART_ITEMS_PATH).headers(headers)
            .body(Map.of("optionId", optionId, "quantity", 1)).exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((CART_ITEMS_PATH + "/").length());
    }

    private static ChildChangedEvent childDeleted(LocalDateTime at) {
        return new ChildChangedEvent(UUID.randomUUID(), at,
            new ChildChangedEvent.Data(CHILD_ID, BUYER_ID, null, null, null, null, null, null, true));
    }

    private static ChildChangedEvent childActive(LocalDateTime at) {
        return new ChildChangedEvent(UUID.randomUUID(), at,
            new ChildChangedEvent.Data(CHILD_ID, BUYER_ID, new BigDecimal("110.0"), new BigDecimal("20.0"),
                Gender.MALE, LocalDate.of(2020, 1, 1), null, null, false));
    }

    private static ChildAccountChangedEvent account(boolean deleted, LocalDateTime at) {
        return new ChildAccountChangedEvent(UUID.randomUUID(), at,
            new ChildAccountChangedEvent.Data(ACCOUNT_ID, CHILD_ID, BUYER_ID, deleted));
    }

    private static MemberWithdrawnEvent withdrawn() {
        return new MemberWithdrawnEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberWithdrawnEvent.Data(BUYER_ID));
    }

    private static Consumer<HttpHeaders> child() {
        return headers -> {
            headers.set(PrincipalHeaders.CHILD_ACCOUNT_ID, String.valueOf(ACCOUNT_ID));
            headers.set(PrincipalHeaders.CHILD_GUARDIAN_ID, String.valueOf(BUYER_ID));
        };
    }
}
