package com.bbcc.kidly.boundedcontext.cart.in.web;

import static com.bbcc.kidly.boundedcontext.cart.in.web.CartController.CART_ITEMS_PATH;
import static com.bbcc.kidly.boundedcontext.cart.in.web.CartController.CART_PATH;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartReservations;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderQueryService;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.event.ChildAccountEventListener;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.math.BigDecimal;
import java.net.URI;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 장바구니 주인 일반화(#998): /api/v1/cart는 회원 · 로그인한 자녀가 각자 "내 장바구니"로 쓴다. 아이 장바구니는 아이만 본다(부모도 403).
 * 다른 영역이 칸을 예약하면 주인은 빼거나 수량을 바꿀 수 없고(409), 허용된 주체만 그 칸으로 주문한다. 결제가 끝나면 산 칸이 빠지고
 * cart.item-purchased(주문 ID · 주문상품 ID, 예약 칸이면 예약 ID)가 나가고 cart.item-removed는 나가지 않는다. 사용자가 빼면 cart.item-removed.
 * 자녀 1 · 2(형제)의 보호자는 BUYER_ID, 자녀 계정 100 → 자녀 1, 101 → 자녀 2, 102 → 복사본 없는 프로필 9.
 */
@FunctionalTest
class CartOwnerApiTest {

    private static final String REMOVED = "cart.item-removed";
    private static final String PURCHASED = "cart.item-purchased";
    private static final Long CHILD_ID = 1L;
    private static final Long SIBLING_ID = 2L;
    private static final Long ACCOUNT_ID = 100L;
    private static final Long SIBLING_ACCOUNT_ID = 101L;
    private static final Long NO_PROFILE_ACCOUNT_ID = 102L;
    private static final Long OTHER_MEMBER_ID = 50L;
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private ChildAccountEventListener childAccountEventListener;

    @Autowired
    private CartReservations cartReservations;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderQueryService orderQueries;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(new ReplicaChild.Values(SIBLING_ID, BUYER_ID, null,
            new BigDecimal("100.0"), new BigDecimal("15.0"), "NORMAL", false, OCCURRED_AT)));
        account(ACCOUNT_ID, CHILD_ID);
        account(SIBLING_ACCOUNT_ID, SIBLING_ID);
        account(NO_PROFILE_ACCOUNT_ID, 9L);
    }

    @Test
    @DisplayName("주인별 분리: 보호자와 자녀가 같은 옵션 · 같은 자녀로 담아도 각자 다른 칸이고, 각자 자기 장바구니만 본다")
    void separatedByOwner() {
        String parentLine = add(TestMembers.buyer(BUYER_ID), Map.of("optionId", "1", "childId", "1", "quantity", 2));
        String childLine = add(child(ACCOUNT_ID), Map.of("optionId", "1", "quantity", 1));

        assertThat(childLine).isNotEqualTo(parentLine);
        client.get().uri(CART_PATH).headers(TestMembers.buyer(BUYER_ID)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].cartItemId").isEqualTo(parentLine)
            .jsonPath("$.items[0].reserved").isEqualTo(false);
        client.get().uri(CART_PATH).headers(child(ACCOUNT_ID)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].cartItemId").isEqualTo(childLine)
            .jsonPath("$.items[0].childId").isEqualTo("1");
        assertThat(jdbcTemplate.queryForMap("select owner_type, owner_id, member_id, child_id from orders.cart_item"
            + " where id = ?", Long.valueOf(childLine)))
            .containsEntry("owner_type", "CHILD").containsEntry("owner_id", CHILD_ID)
            .containsEntry("member_id", BUYER_ID).containsEntry("child_id", CHILD_ID);
        client.get().uri(CART_PATH).headers(child(SIBLING_ACCOUNT_ID)).exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.items.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("아이 장바구니는 아이만: 부모 · 형제는 빼기 · 수량 변경 403, 남의 회원은 404. 아이도 부모 칸은 403")
    void othersCannotTouchChildCart() {
        String childLine = add(child(ACCOUNT_ID), Map.of("optionId", "1", "quantity", 1));
        String parentLine = add(TestMembers.buyer(BUYER_ID), Map.of("optionId", "1", "quantity", 1));

        for (Consumer<HttpHeaders> other : List.of(TestMembers.buyer(BUYER_ID), child(SIBLING_ACCOUNT_ID))) {
            client.delete().uri(CART_ITEMS_PATH + "/" + childLine).headers(other).exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.title").isEqualTo("다른 사람의 장바구니입니다");
            client.patch().uri(CART_ITEMS_PATH + "/" + childLine).headers(other).body(Map.of("quantity", 3)).exchange()
                .expectStatus().isForbidden();
        }
        client.delete().uri(CART_ITEMS_PATH + "/" + childLine).headers(TestMembers.buyer(OTHER_MEMBER_ID)).exchange()
            .expectStatus().isNotFound();
        client.delete().uri(CART_ITEMS_PATH + "/" + parentLine).headers(child(ACCOUNT_ID)).exchange()
            .expectStatus().isForbidden();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item", Long.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("아이 장바구니 담기: 다른 자녀를 고르면 400, 프로필 복사본이 없으면 404(CART-C-01), 계정 연결 없음 403 · 헤더 없음 401")
    void childAddRules() {
        client.post().uri(CART_ITEMS_PATH).headers(child(ACCOUNT_ID))
            .body(Map.of("optionId", "1", "childId", String.valueOf(SIBLING_ID), "quantity", 1)).exchange()
            .expectStatus().isBadRequest()
            .expectBody().jsonPath("$.title").isEqualTo("다른 자녀를 고를 수 없습니다");
        client.post().uri(CART_ITEMS_PATH).headers(child(NO_PROFILE_ACCOUNT_ID))
            .body(Map.of("optionId", "1", "quantity", 1)).exchange()
            .expectStatus().isNotFound()
            .expectBody().jsonPath("$.title").isEqualTo("자녀를 찾을 수 없습니다");
        client.get().uri(CART_PATH).headers(child(999L)).exchange()
            .expectStatus().isForbidden()
            .expectBody().jsonPath("$.title").isEqualTo("장바구니를 쓸 수 없는 자녀 계정입니다");
        client.get().uri(CART_PATH).exchange().expectStatus().isUnauthorized();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item", Long.class)).isZero();
    }

    @Test
    @DisplayName("예약된 칸은 주인이 빼거나 수량을 바꾸면 409이고 조회에 reserved true. 예약을 풀면 다시 뺄 수 있다(cart.item-removed)")
    void reservedLineIsLocked() {
        String childLine = add(child(ACCOUNT_ID), Map.of("optionId", "1", "quantity", 1));
        reserve(childLine, BUYER_ID, "wish-7");

        client.delete().uri(CART_ITEMS_PATH + "/" + childLine).headers(child(ACCOUNT_ID)).exchange()
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.title").isEqualTo("예약된 장바구니 상품입니다");
        client.patch().uri(CART_ITEMS_PATH + "/" + childLine).headers(child(ACCOUNT_ID)).body(Map.of("quantity", 3))
            .exchange()
            .expectStatus().isEqualTo(409);
        client.get().uri(CART_PATH).headers(child(ACCOUNT_ID)).exchange()
            .expectBody().jsonPath("$.items[0].reserved").isEqualTo(true);

        transactions.executeWithoutResult(status -> cartReservations.release("wish-7"));
        jdbcTemplate.update("delete from orders.order_outbox");
        client.delete().uri(CART_ITEMS_PATH + "/" + childLine).headers(child(ACCOUNT_ID)).exchange()
            .expectStatus().isNoContent();
        JsonNode data = eventData(REMOVED);
        assertThat(data.get("ownerType").asString()).isEqualTo("CHILD");
        assertThat(data.get("ownerId").asLong()).isEqualTo(CHILD_ID);
        // 원인 · 주문 · 예약 칸은 없다(#998에서 cart.item-purchased로 나눔)
        assertThat(data.has("reason")).isFalse();
        assertThat(data.has("orderId")).isFalse();
        assertThat(data.has("reservationId")).isFalse();
        assertThat(eventCount(PURCHASED)).isZero();
    }

    @Test
    @DisplayName("허용 주체만 그 칸으로 주문한다: 예약 전(같은 가족) 403 · 다른 회원 404, 허용된 보호자는 201. 결제가 끝나면 그 칸이 빠지고 "
        + "cart.item-purchased(주문 ID · 주문상품 ID · 예약 ID) 한 건, cart.item-removed 없음")
    void onlyAllowedPayerOrdersAndPurchaseClears() {
        String childLine = add(child(ACCOUNT_ID), Map.of("optionId", "1", "quantity", 1));
        Map<String, Object> line = Map.of("optionId", "1", "childId", "1", "quantity", 1, "unitPrice", 25_000,
            "cartItemId", childLine);

        order(TestMembers.buyer(BUYER_ID), line)
            .expectStatus().isForbidden()
            .expectBody().jsonPath("$.title").isEqualTo("주문할 수 없는 장바구니 상품입니다");
        reserve(childLine, BUYER_ID, "wish-8");
        // 다른 가족의 칸은 없는 칸과 같다(존재를 숨김)
        order(TestMembers.buyer(OTHER_MEMBER_ID), line).expectStatus().isNotFound();
        URI location = order(TestMembers.buyer(BUYER_ID), line)
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        Long orderId = Long.valueOf(location.getPath().substring((ORDERS_PATH + "/").length()));
        assertThat(jdbcTemplate.queryForObject("select cart_item_id from orders.order_item where order_id = ?",
            Long.class, orderId)).isEqualTo(Long.valueOf(childLine));

        jdbcTemplate.update("delete from orders.order_outbox");
        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(orderId)));
        eventDelivery.deliver(PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, orderId), BUYER_ID));

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item where id = ?", Long.class,
            Long.valueOf(childLine))).isZero();
        JsonNode data = eventData(PURCHASED);
        assertThat(data.get("cartItemId").asLong()).isEqualTo(Long.parseLong(childLine));
        assertThat(data.get("ownerType").asString()).isEqualTo("CHILD");
        assertThat(data.get("ownerId").asLong()).isEqualTo(CHILD_ID);
        assertThat(data.get("memberId").asLong()).isEqualTo(BUYER_ID);
        assertThat(data.get("optionId").asLong()).isEqualTo(1L);
        assertThat(data.get("childId").asLong()).isEqualTo(CHILD_ID);
        assertThat(data.get("quantity").asInt()).isEqualTo(1);
        assertThat(data.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(data.get("orderItemId").asLong()).isEqualTo(orderItemIdOf(orderId));
        assertThat(data.get("reservationId").asString()).isEqualTo("wish-8");
        assertThat(eventCount(REMOVED)).isZero();
    }

    @Test
    @DisplayName("예약 → 허용 보호자 주문 → 예약 해제 → 결제 완료여도 주문에 기록된 아이 칸이 빠지고 cart.item-purchased(예약 ID 없음)")
    void purchaseClearsRecordedLineEvenIfReleased() {
        String childLine = add(child(ACCOUNT_ID), Map.of("optionId", "1", "quantity", 1));
        reserve(childLine, BUYER_ID, "wish-9");
        Long orderId = Long.valueOf(order(TestMembers.buyer(BUYER_ID), Map.of("optionId", "1", "childId", "1",
                "quantity", 1, "unitPrice", 25_000, "cartItemId", childLine))
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation().getPath()
            .substring((ORDERS_PATH + "/").length()));
        transactions.executeWithoutResult(status -> cartReservations.release("wish-9"));

        jdbcTemplate.update("delete from orders.order_outbox");
        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(orderId)));
        eventDelivery.deliver(PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, orderId), BUYER_ID));

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item where id = ?", Long.class,
            Long.valueOf(childLine))).isZero();
        JsonNode data = eventData(PURCHASED);
        assertThat(data.get("cartItemId").asLong()).isEqualTo(Long.parseLong(childLine));
        assertThat(data.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(data.get("reservationId").isNull()).isTrue();
        assertThat(eventCount(REMOVED)).isZero();
    }

    @Test
    @DisplayName("칸 ID 없이 주문해도 결제가 끝나면 구매자의 같은 (옵션, 자녀) 칸이 빠지고 cart.item-purchased로 나간다(예약 ID 없음). "
        + "같은 (옵션, 자녀)의 아이 칸은 남는다")
    void purchaseClearsBuyersLineWithoutCartItemId() {
        String parentLine = add(TestMembers.buyer(BUYER_ID), Map.of("optionId", "1", "childId", "1", "quantity", 1));
        String childLine = add(child(ACCOUNT_ID), Map.of("optionId", "1", "quantity", 1));
        Long orderId = Long.valueOf(order(TestMembers.buyer(BUYER_ID),
                Map.of("optionId", "1", "childId", "1", "quantity", 1, "unitPrice", 25_000))
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation().getPath()
            .substring((ORDERS_PATH + "/").length()));

        jdbcTemplate.update("delete from orders.order_outbox");
        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(orderId)));
        eventDelivery.deliver(PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, orderId), BUYER_ID));

        JsonNode data = eventData(PURCHASED);
        assertThat(data.get("cartItemId").asLong()).isEqualTo(Long.parseLong(parentLine));
        assertThat(data.get("ownerType").asString()).isEqualTo("MEMBER");
        assertThat(data.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(data.get("orderItemId").asLong()).isEqualTo(orderItemIdOf(orderId));
        assertThat(data.get("reservationId").isNull()).isTrue();
        assertThat(eventCount(REMOVED)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item where id = ?", Long.class,
            Long.valueOf(childLine))).as("아이 칸은 남는다").isEqualTo(1);
    }

    // 그 종류의 아웃박스 행이 꼭 하나여야 한다(queryForObject)
    private JsonNode eventData(String eventType) {
        String payload = jdbcTemplate.queryForObject(
            "select payload from orders.order_outbox where event_type = ?", String.class, eventType);
        return jsonMapper.readTree(payload).get("data");
    }

    private long eventCount(String eventType) {
        return jdbcTemplate.queryForObject("select count(*) from orders.order_outbox where event_type = ?",
            Long.class, eventType);
    }

    private Long orderItemIdOf(Long orderId) {
        return jdbcTemplate.queryForObject("select id from orders.order_item where order_id = ?", Long.class,
            orderId);
    }

    private void reserve(String cartItemId, Long payerMemberId, String reservationId) {
        transactions.executeWithoutResult(status -> cartReservations.reserve(List.of(Long.valueOf(cartItemId)),
            List.of(new CartReservations.Payer("MEMBER", payerMemberId)), reservationId));
    }

    private RestTestClient.ResponseSpec order(Consumer<HttpHeaders> headers, Map<String, Object> line) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(line));
        body.put("shippingAddress", ADDRESS);
        return client.post().uri(ORDERS_PATH).headers(headers).body(body).exchange();
    }

    private String add(Consumer<HttpHeaders> headers, Map<String, Object> body) {
        URI location = client.post().uri(CART_ITEMS_PATH).headers(headers).body(body).exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((CART_ITEMS_PATH + "/").length());
    }

    private void account(Long accountId, Long childProfileId) {
        childAccountEventListener.onChildAccountChanged(new ChildAccountChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new ChildAccountChangedEvent.Data(accountId, childProfileId, BUYER_ID, false)));
    }

    private static Consumer<HttpHeaders> child(Long accountId) {
        return headers -> {
            headers.set(PrincipalHeaders.CHILD_ACCOUNT_ID, String.valueOf(accountId));
            headers.set(PrincipalHeaders.CHILD_GUARDIAN_ID, String.valueOf(BUYER_ID));
        };
    }
}
