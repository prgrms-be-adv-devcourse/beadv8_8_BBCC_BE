package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.cart.in.web.CartController.CART_ITEMS_PATH;
import static com.bbcc.kidly.boundedcontext.delivery.in.web.SellerOrderController.SELLER_ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.in.web.ChildPurchaseController.CHILD_PURCHASES_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.wish.in.web.WishRequestController.WISH_REQUESTS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 자녀가 보는 나를 위해 산 상품 내역(#1000, BEG-07, GET /api/v1/orders/items/mine): 자녀 로그인만, 자기가 입을 아이인 주문상품 중
 * 결제된 적 있는 주문의 것만 최신순 목록 형식으로. 출처 칸은 없고, 화면이 조르기 응답의 paidOrderItemIds와 orderItemId를 맞춰
 * 조르기로 산 것 / 보호자가 산 것을 표시한다(그 대조가 되는지도 여기서 본다).
 * 자녀 1 · 2(형제)의 보호자는 BUYER_ID, 자녀 계정 100 → 1, 101 → 2. 옵션 1(25,000원) · 2(12,000원).
 */
@FunctionalTest
class ChildPurchaseApiTest {

    private static final Long CHILD_ID = 1L;
    private static final String THUMBNAIL = "https://cdn.kidly.test/products/1/thumb.jpg";
    private static final Long SIBLING_ID = 2L;
    private static final Long ACCOUNT_ID = 100L;
    private static final Long SIBLING_ACCOUNT_ID = 101L;
    private static final long PRICE = 25_000L;
    private static final long SECOND_PRICE = 12_000L;
    private static final Long SECOND_SELLER_ID = 3L;
    private static final Long THIRD_SELLER_ID = 4L;
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
            ReplicaFixture.product(2L, SECOND_PRICE, "ON_SALE", false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(new ReplicaChild.Values(SIBLING_ID, BUYER_ID, null,
            new BigDecimal("100.0"), new BigDecimal("15.0"), "NORMAL", false, OCCURRED_AT)));
        account(ACCOUNT_ID, CHILD_ID);
        account(SIBLING_ACCOUNT_ID, SIBLING_ID);
    }

    @Test
    @DisplayName("결제된 적 있는 내 주문상품만 최신순(출처 칸 없음), 결제 뒤 취소는 CANCELLED. 결제대기 · 형제 · 자녀 없는 주문상품은 "
        + "빠진다. 조르기 상세 · 목록의 paidOrderItemIds가 조르기로 산 줄만 가리킨다")
    void listsPaidPurchases() {
        // 옵션 1 상품에만 썸네일(#1328): 주문상품은 주문 시점 스냅샷, 조르기 줄은 지금 복사본 값
        jdbcTemplate.update("update orders.replica_product set thumbnail_url = ? where option_id = 1", THUMBNAIL);
        // 조르기로 산 것
        String line = addToCart(child(ACCOUNT_ID), "1");
        String requestId = sendWish(line);
        Long wishOrder = order(Map.of("optionId", "1", "childId", "1", "quantity", 1, "unitPrice", PRICE,
            "cartItemId", line));
        pay(wishOrder);
        // 보호자가 직접(칸 없이) 산 것 → 결제 뒤 취소
        Long cancelledOrder = order(Map.of("optionId", "2", "childId", "1", "quantity", 2, "unitPrice", SECOND_PRICE));
        pay(cancelledOrder);
        client.post().uri(ORDERS_PATH + "/" + cancelledOrder + "/cancel").headers(TestMembers.buyer(BUYER_ID))
            .exchange().expectStatus().isOk();
        // 보호자가 직접 산 것
        Long parentOrder = order(Map.of("optionId", "1", "childId", "1", "quantity", 3, "unitPrice", PRICE));
        pay(parentOrder);
        // 빠지는 것: 결제대기 · 형제 · 자녀 없음
        order(Map.of("optionId", "1", "childId", "1", "quantity", 1, "unitPrice", PRICE));
        pay(order(Map.of("optionId", "1", "childId", "2", "quantity", 1, "unitPrice", PRICE)));
        pay(order(Map.of("optionId", "1", "quantity", 1, "unitPrice", PRICE)));

        JsonNode body = purchases(child(ACCOUNT_ID), "");

        assertThat(body.get("totalElements").asLong()).isEqualTo(3);
        JsonNode content = body.get("content");
        assertThat(content).hasSize(3);
        assertLine(content.get(0), itemOf(parentOrder), "PAID", 3);
        assertLine(content.get(1), itemOf(cancelledOrder), "CANCELLED", 2);
        assertLine(content.get(2), itemOf(wishOrder), "PAID", 1);
        assertThat(content.get(2).get("productName").asString()).isNotBlank();
        assertThat(content.get(2).get("optionName").asString()).isNotBlank();
        assertThat(content.get(2).get("orderedAt").isNull()).isFalse();
        // 가격 · 배송지 · 판매자 · 주문 ID(주문 상세 링크) · 출처는 없다
        content.forEach(row -> assertThat(row.propertyNames()).containsExactly("orderItemId", "orderedAt",
            "productId", "productName", "optionName", "quantity", "status", "thumbnailUrl"));
        assertThat(content.get(0).get("thumbnailUrl").asString()).isEqualTo(THUMBNAIL);
        assertThat(content.get(1).get("thumbnailUrl").isNull()).as("썸네일 없는 상품").isTrue();
        assertThat(content.get(2).get("thumbnailUrl").asString()).isEqualTo(THUMBNAIL);
        assertThat(purchases(child(SIBLING_ACCOUNT_ID), "").get("totalElements").asLong()).isOne();
        // 화면 합치기: 조르기 상세 · 목록의 paidOrderItemIds는 조르기로 산 주문상품만 가리킨다
        JsonNode wish = get(WISH_REQUESTS_PATH + "/" + requestId, child(ACCOUNT_ID));
        assertThat(wish.get("paidOrderItemIds")).extracting(JsonNode::asString).containsExactly(itemOf(wishOrder));
        assertThat(wish.get("items").get(0).get("thumbnailUrl").asString()).as("조르기 줄(#1328)").isEqualTo(THUMBNAIL);
        JsonNode wishes = get(WISH_REQUESTS_PATH, child(ACCOUNT_ID));
        assertThat(wishes).singleElement().satisfies(row -> assertThat(row.get("paidOrderItemIds"))
            .extracting(JsonNode::asString).containsExactly(itemOf(wishOrder)));
        assertThat(get(WISH_REQUESTS_PATH + "/" + requestId, TestMembers.buyer(BUYER_ID)).get("paidOrderItemIds"))
            .extracting(JsonNode::asString).containsExactly(itemOf(wishOrder));
    }

    @Test
    @DisplayName("상태는 판매자별로 따라간다: 발송하면 그 줄 SHIPPING, 배송 완료하면 DELIVERED, 판매자 부분 취소는 그 줄만 CANCELLED이고 "
        + "다른 판매자 줄은 그대로")
    void statusFollowsSellerDelivery() {
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            3L, 3L, SECOND_SELLER_ID, "상품 C", null, "120", SECOND_PRICE, 2L, "ON_SALE", null, false, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            4L, 4L, THIRD_SELLER_ID, "상품 D", null, "120", SECOND_PRICE, 2L, "ON_SALE", null, false, OCCURRED_AT)));
        Long orderId = orderOf(List.of(
            Map.of("optionId", "1", "childId", "1", "quantity", 1, "unitPrice", PRICE),
            Map.of("optionId", "3", "childId", "1", "quantity", 1, "unitPrice", SECOND_PRICE),
            Map.of("optionId", "4", "childId", "1", "quantity", 1, "unitPrice", SECOND_PRICE)));
        pay(orderId);
        String first = itemOf(orderId, 1L);
        String cancelled = itemOf(orderId, 3L);
        String other = itemOf(orderId, 4L);
        assertThat(statuses()).containsEntry(first, "PAID").containsEntry(cancelled, "PAID")
            .containsEntry(other, "PAID");

        client.post().uri(SELLER_ORDERS_PATH + "/" + deliveryOf(orderId, ReplicaFixture.SELLER_ID) + "/ship")
            .headers(TestMembers.seller(ReplicaFixture.SELLER_ID))
            .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890")).exchange()
            .expectStatus().isOk();
        assertThat(statuses()).containsEntry(first, "SHIPPING").containsEntry(cancelled, "PAID")
            .containsEntry(other, "PAID");

        client.post().uri(SELLER_ORDERS_PATH + "/" + deliveryOf(orderId, ReplicaFixture.SELLER_ID) + "/deliver")
            .headers(TestMembers.seller(ReplicaFixture.SELLER_ID)).exchange()
            .expectStatus().isOk();
        client.post().uri(SELLER_ORDERS_PATH + "/" + deliveryOf(orderId, SECOND_SELLER_ID) + "/cancel")
            .headers(TestMembers.seller(SECOND_SELLER_ID))
            .body(Map.of("reason", "OUT_OF_STOCK")).exchange()
            .expectStatus().isOk();

        assertThat(statuses()).containsEntry(first, "DELIVERED").containsEntry(cancelled, "CANCELLED")
            .containsEntry(other, "PAID");
    }

    @Test
    @DisplayName("팀 목록 형식 · 최신순 페이지: size 2면 두 쪽에 나뉜다")
    void paged() {
        Long first = order(Map.of("optionId", "1", "childId", "1", "quantity", 1, "unitPrice", PRICE));
        pay(first);
        Long second = order(Map.of("optionId", "2", "childId", "1", "quantity", 1, "unitPrice", SECOND_PRICE));
        pay(second);
        Long third = order(Map.of("optionId", "1", "childId", "1", "quantity", 2, "unitPrice", PRICE));
        pay(third);

        JsonNode page0 = purchases(child(ACCOUNT_ID), "?page=0&size=2");
        JsonNode page1 = purchases(child(ACCOUNT_ID), "?page=1&size=2");

        assertThat(page0.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.get("hasNext").asBoolean()).isTrue();
        assertThat(page0.get("content")).extracting(row -> row.get("orderItemId").asString())
            .containsExactly(itemOf(third), itemOf(second));
        assertThat(page1.get("hasNext").asBoolean()).isFalse();
        assertThat(page1.get("content")).extracting(row -> row.get("orderItemId").asString())
            .containsExactly(itemOf(first));
        client.get().uri(CHILD_PURCHASES_PATH + "?size=101").headers(child(ACCOUNT_ID)).exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("보호자(회원) 토큰은 403, 헤더 없음 401, 자녀 계정 연결을 확인할 수 없거나(없는 계정) 보호자가 다르면 403")
    void childOnly() {
        client.get().uri(CHILD_PURCHASES_PATH).headers(TestMembers.buyer(BUYER_ID)).exchange()
            .expectStatus().isForbidden()
            .expectBody().jsonPath("$.title").isEqualTo("자녀만 볼 수 있습니다");
        client.get().uri(CHILD_PURCHASES_PATH).exchange().expectStatus().isUnauthorized();
        client.get().uri(CHILD_PURCHASES_PATH).headers(headers -> headers.set(PrincipalHeaders.CHILD_ACCOUNT_ID, "100"))
            .exchange().expectStatus().isUnauthorized();
        client.get().uri(CHILD_PURCHASES_PATH).headers(child(999L)).exchange()
            .expectStatus().isForbidden()
            .expectBody().jsonPath("$.title").isEqualTo("구매 내역을 볼 수 없는 자녀 계정입니다");
        // 있는 자녀 계정이지만 토큰의 보호자 ID가 복사본의 보호자와 다르다
        client.get().uri(CHILD_PURCHASES_PATH).headers(headers -> {
            headers.set(PrincipalHeaders.CHILD_ACCOUNT_ID, String.valueOf(ACCOUNT_ID));
            headers.set(PrincipalHeaders.CHILD_GUARDIAN_ID, String.valueOf(BUYER_ID + 1000));
        }).exchange()
            .expectStatus().isForbidden()
            .expectBody().jsonPath("$.title").isEqualTo("구매 내역을 볼 수 없는 자녀 계정입니다");
    }

    private void assertLine(JsonNode row, String orderItemId, String status, int quantity) {
        assertThat(row.get("orderItemId").asString()).isEqualTo(orderItemId);
        assertThat(row.get("status").asString()).isEqualTo(status);
        assertThat(row.get("quantity").asInt()).isEqualTo(quantity);
    }

    private JsonNode get(String uri, Consumer<HttpHeaders> headers) {
        return jsonMapper.readTree(client.get().uri(uri).headers(headers).exchange()
            .expectStatus().isOk()
            .returnResult(String.class).getResponseBody());
    }

    private Map<String, String> statuses() {
        Map<String, String> statuses = new HashMap<>();
        purchases(child(ACCOUNT_ID), "").get("content")
            .forEach(row -> statuses.put(row.get("orderItemId").asString(), row.get("status").asString()));
        return statuses;
    }

    private String itemOf(Long orderId, Long optionId) {
        return String.valueOf(jdbcTemplate.queryForObject(
            "select id from orders.order_item where order_id = ? and option_id = ?", Long.class, orderId, optionId));
    }

    private Long deliveryOf(Long orderId, Long sellerId) {
        return jdbcTemplate.queryForObject("select id from orders.delivery where order_id = ? and seller_id = ?",
            Long.class, orderId, sellerId);
    }

    private JsonNode purchases(Consumer<HttpHeaders> headers, String query) {
        String body = client.get().uri(CHILD_PURCHASES_PATH + query).headers(headers).exchange()
            .expectStatus().isOk()
            .returnResult(String.class).getResponseBody();
        return jsonMapper.readTree(body);
    }

    private String itemOf(Long orderId) {
        return String.valueOf(jdbcTemplate.queryForObject("select id from orders.order_item where order_id = ?",
            Long.class, orderId));
    }

    private void pay(Long orderId) {
        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(orderId)));
        eventDelivery.deliver(PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, orderId), BUYER_ID));
    }

    private Long order(Map<String, Object> line) {
        return orderOf(List.of(line));
    }

    private Long orderOf(List<Map<String, Object>> lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", lines);
        body.put("shippingAddress", ADDRESS);
        URI location = client.post().uri(ORDERS_PATH).headers(TestMembers.buyer(BUYER_ID)).body(body).exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return Long.valueOf(location.getPath().substring((ORDERS_PATH + "/").length()));
    }

    private String sendWish(String cartItemId) {
        URI location = client.post().uri(WISH_REQUESTS_PATH).headers(child(ACCOUNT_ID))
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
