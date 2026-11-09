package com.bbcc.kidly.boundedcontext.wish.in.web;

import static com.bbcc.kidly.boundedcontext.cart.in.web.CartController.CART_ITEMS_PATH;
import static com.bbcc.kidly.boundedcontext.cart.in.web.CartController.CART_PATH;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.wish.in.web.WishRequestController.WISH_REQUESTS_PATH;
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
 * 구매 요청(조르기, #999): 자녀가 자기 장바구니 칸을 골라 보내면 칸이 예약되고, 승인자(보호자)가 그 칸으로 주문해 결제가 끝나면
 * 결제 완료가 된다(승인 = 결제). 테스트 설정은 주문 Kafka 수신이 꺼져 있어 결제 완료는 Spring 되돌리기 경로(커밋 뒤)로 들어온다.
 * 자녀 1 · 2(형제)의 보호자는 BUYER_ID, 자녀 3은 다른 가족(보호자 50). 자녀 계정 100 → 1, 101 → 2, 103 → 3.
 */
@FunctionalTest
class WishRequestApiTest {

    private static final Long CHILD_ID = 1L;
    private static final Long SIBLING_ID = 2L;
    private static final Long OTHER_CHILD_ID = 3L;
    private static final Long OTHER_MEMBER_ID = 50L;
    private static final Long ACCOUNT_ID = 100L;
    private static final Long SIBLING_ACCOUNT_ID = 101L;
    private static final Long OTHER_ACCOUNT_ID = 103L;
    private static final long PRICE = 25_000L;
    private static final Long SECOND_OPTION = 2L;
    private static final long SECOND_PRICE = 12_000L;
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
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(PRICE, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.product(SECOND_OPTION, SECOND_PRICE, "ON_SALE", false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(childReplica(SIBLING_ID, BUYER_ID)));
        childReplicaRepository.save(ReplicaChild.create(childReplica(OTHER_CHILD_ID, OTHER_MEMBER_ID)));
        account(ACCOUNT_ID, CHILD_ID, BUYER_ID);
        account(SIBLING_ACCOUNT_ID, SIBLING_ID, BUYER_ID);
        account(OTHER_ACCOUNT_ID, OTHER_CHILD_ID, OTHER_MEMBER_ID);
    }

    @Test
    @DisplayName("보내기: 자녀가 내 장바구니 칸을 골라 보내면 201 · 대기이고, 칸이 예약된다(결제 허용 = 보호자, 예약 ID wish-{ID}). "
        + "wish.sent(키 = 자녀 프로필 ID)")
    void sendReservesLines() {
        String first = addToCart(child(ACCOUNT_ID, BUYER_ID), "1", 2);
        String second = addToCart(child(ACCOUNT_ID, BUYER_ID), "2", 1);

        send(child(ACCOUNT_ID, BUYER_ID), List.of(first, second), "  생일 선물로 사 주세요 ")
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.type").isEqualTo("WISH")
            .jsonPath("$.childId").isEqualTo("1")
            .jsonPath("$.status").isEqualTo("REQUESTED")
            .jsonPath("$.requestedByType").isEqualTo("CHILD")
            .jsonPath("$.requestedById").isEqualTo("1")
            .jsonPath("$.message").isEqualTo("생일 선물로 사 주세요")
            .jsonPath("$.items.length()").isEqualTo(2)
            .jsonPath("$.items[0].cartItemId").isEqualTo(first)
            .jsonPath("$.items[0].quantity").isEqualTo(2)
            .jsonPath("$.items[0].unitPrice").isEqualTo(PRICE)
            .jsonPath("$.items[0].purchased").isEqualTo(false)
            .jsonPath("$.items[1].cartItemId").isEqualTo(second)
            .jsonPath("$.purchases.length()").isEqualTo(0);
        String requestId = latestRequestId();

        client.get().uri(CART_PATH).headers(child(ACCOUNT_ID, BUYER_ID)).exchange()
            .expectBody()
            .jsonPath("$.items[0].reserved").isEqualTo(true)
            .jsonPath("$.items[1].reserved").isEqualTo(true);
        assertThat(jdbcTemplate.queryForList(
            "select reservation_id, payer_type, payer_id from orders.cart_reservation"))
            .singleElement().satisfies(row -> {
                assertThat(row).containsEntry("reservation_id", "wish-" + requestId)
                    .containsEntry("payer_type", "MEMBER").containsEntry("payer_id", BUYER_ID);
            });
        JsonNode sent = outbox("wish.sent").getFirst();
        assertThat(sent.get("correlationId").asString()).isEqualTo("1");
        assertThat(sent.get("data").get("wishRequestId").asString()).isEqualTo(requestId);
        assertThat(sent.get("data").get("memberId").asLong()).isEqualTo(BUYER_ID);
        assertThat(sent.get("data").get("items")).hasSize(2);
    }

    @Test
    @DisplayName("보내기 규칙: 회원 403 · 빈 칸 400 · 남의 칸 404 · 진행 중 칸 409(BEG-02) · 다른 곳이 예약한 칸 409 · 긴 메시지 400. "
        + "실패하면 조르기도 예약도 남지 않는다")
    void sendRules() {
        String line = addToCart(child(ACCOUNT_ID, BUYER_ID), "1", 1);
        String other = addToCart(child(ACCOUNT_ID, BUYER_ID), "2", 1);
        String parentLine = addToCart(TestMembers.buyer(BUYER_ID), "1", 1);
        String siblingLine = addToCart(child(SIBLING_ACCOUNT_ID, BUYER_ID), "1", 1);

        send(TestMembers.buyer(BUYER_ID), List.of(parentLine), null).expectStatus().isForbidden()
            .expectBody().jsonPath("$.title").isEqualTo("조르기를 보낼 수 없습니다");
        send(child(ACCOUNT_ID, BUYER_ID), List.of(), null).expectStatus().isBadRequest();
        send(child(ACCOUNT_ID, BUYER_ID), List.of(line, line), null).expectStatus().isBadRequest();
        send(child(ACCOUNT_ID, BUYER_ID), List.of(parentLine), null).expectStatus().isNotFound()
            .expectBody().jsonPath("$.title").isEqualTo("장바구니 상품을 찾을 수 없습니다");
        send(child(ACCOUNT_ID, BUYER_ID), List.of(siblingLine), null).expectStatus().isNotFound();
        send(child(ACCOUNT_ID, BUYER_ID), List.of(line), "가".repeat(101)).expectStatus().isBadRequest();
        assertThat(count("orders.wish_request")).isZero();

        send(child(ACCOUNT_ID, BUYER_ID), List.of(line), null).expectStatus().isCreated();
        send(child(ACCOUNT_ID, BUYER_ID), List.of(other, line), null).expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.title").isEqualTo("조르는 중인 상품입니다");
        transactions.executeWithoutResult(status -> cartReservations.reserve(List.of(Long.valueOf(other)),
            List.of(new CartReservations.Payer("MEMBER", BUYER_ID)), "other-1", null));
        send(child(ACCOUNT_ID, BUYER_ID), List.of(other), null).expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.title").isEqualTo("예약된 장바구니 상품입니다");

        assertThat(count("orders.wish_request")).isOne();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_reservation where reservation_id"
            + " like 'wish-%'", Long.class)).isOne();
    }

    @Test
    @DisplayName("볼 수 있는 사람: 보낸 자녀 · 승인자(보호자)만. 형제 자녀는 상세 403 · 목록에 없음, 다른 가족은 404")
    void visibleToRequesterAndApprover() {
        String requestId = sendOne(addToCart(child(ACCOUNT_ID, BUYER_ID), "1", 1));
        // 다른 가족의 조르기는 보호자 목록에 섞이지 않는다
        sendOne(child(OTHER_ACCOUNT_ID, OTHER_MEMBER_ID), addToCart(child(OTHER_ACCOUNT_ID, OTHER_MEMBER_ID), "1", 1));

        for (Consumer<HttpHeaders> viewer : List.of(child(ACCOUNT_ID, BUYER_ID), TestMembers.buyer(BUYER_ID))) {
            client.get().uri(WISH_REQUESTS_PATH).headers(viewer).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].requestId").isEqualTo(requestId);
            client.get().uri(WISH_REQUESTS_PATH + "/" + requestId).headers(viewer).exchange().expectStatus().isOk();
            client.get().uri(WISH_REQUESTS_PATH + "/" + requestId + "/timeline").headers(viewer).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.steps[0].step").isEqualTo("SENT")
                .jsonPath("$.steps[1].step").isEqualTo("PAID")
                .jsonPath("$.steps[1].status").isEqualTo("WAITING");
        }
        client.get().uri(WISH_REQUESTS_PATH + "?childId=" + SIBLING_ID).headers(TestMembers.buyer(BUYER_ID)).exchange()
            .expectBody().jsonPath("$.length()").isEqualTo(0);
        client.get().uri(WISH_REQUESTS_PATH).headers(child(SIBLING_ACCOUNT_ID, BUYER_ID)).exchange()
            .expectBody().jsonPath("$.length()").isEqualTo(0);
        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId).headers(child(SIBLING_ACCOUNT_ID, BUYER_ID)).exchange()
            .expectStatus().isForbidden()
            .expectBody().jsonPath("$.title").isEqualTo("다른 자녀의 조르기입니다");
        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId).headers(TestMembers.buyer(OTHER_MEMBER_ID)).exchange()
            .expectStatus().isNotFound();
        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId).headers(child(OTHER_ACCOUNT_ID, OTHER_MEMBER_ID))
            .exchange()
            .expectStatus().isNotFound();
        client.get().uri(WISH_REQUESTS_PATH).exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("거절: 승인자만(자녀 403), 사유가 목록에 없으면 400. 거절하면 예약이 풀려 칸을 뺄 수 있고 wish.rejected. 다시 처리하면 409")
    void reject() {
        String line = addToCart(child(ACCOUNT_ID, BUYER_ID), "1", 1);
        String requestId = sendOne(line);

        reject(child(ACCOUNT_ID, BUYER_ID), requestId, "NEXT_TIME").expectStatus().isForbidden()
            .expectBody().jsonPath("$.title").isEqualTo("조르기를 거절할 수 없습니다");
        reject(TestMembers.buyer(OTHER_MEMBER_ID), requestId, "NEXT_TIME").expectStatus().isNotFound();
        reject(TestMembers.buyer(BUYER_ID), requestId, "SOMETHING").expectStatus().isBadRequest();
        reject(TestMembers.buyer(BUYER_ID), requestId, "NEXT_TIME").expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("REJECTED")
            .jsonPath("$.rejectReason").isEqualTo("NEXT_TIME")
            .jsonPath("$.rejectedAt").isNotEmpty();

        reject(TestMembers.buyer(BUYER_ID), requestId, "OTHER").expectStatus().isEqualTo(409);
        cancel(child(ACCOUNT_ID, BUYER_ID), requestId).expectStatus().isEqualTo(409);
        JsonNode rejected = outbox("wish.rejected").getFirst().get("data");
        assertThat(rejected.get("reason").asString()).isEqualTo("NEXT_TIME");
        assertThat(count("orders.cart_reservation")).isZero();
        client.delete().uri(CART_ITEMS_PATH + "/" + line).headers(child(ACCOUNT_ID, BUYER_ID)).exchange()
            .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("철회: 보낸 사람만(보호자 403). 철회하면 CANCELLED · 원인 DIRECT, 예약이 풀리고 wish.cancelled(cancelReason DIRECT). "
        + "같은 칸으로 다시 조를 수 있다")
    void cancel() {
        String line = addToCart(child(ACCOUNT_ID, BUYER_ID), "1", 1);
        String requestId = sendOne(line);

        cancel(TestMembers.buyer(BUYER_ID), requestId).expectStatus().isForbidden()
            .expectBody().jsonPath("$.title").isEqualTo("조르기를 철회할 수 없습니다");
        cancel(child(ACCOUNT_ID, BUYER_ID), requestId).expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")
            .jsonPath("$.cancelReason").isEqualTo("DIRECT")
            .jsonPath("$.cancelledAt").isNotEmpty();

        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId + "/timeline").headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectBody()
            .jsonPath("$.steps[1].step").isEqualTo("CANCELLED")
            .jsonPath("$.cancelReason").isEqualTo("DIRECT")
            .jsonPath("$.cancelledBy").isEqualTo("CHILD");
        assertThat(outbox("wish.cancelled").getFirst().get("data").get("cancelReason").asString()).isEqualTo("DIRECT");
        assertThat(count("orders.cart_reservation")).isZero();
        send(child(ACCOUNT_ID, BUYER_ID), List.of(line), null).expectStatus().isCreated();
    }

    @Test
    @DisplayName("승인 = 결제: 보호자가 예약 칸 일부를 주문하고 결제가 끝나면 PAID(결제한 사람 · 산 것), 다 산 칸은 빠지고 "
        + "남은 칸은 예약이 풀린다. wish.paid는 한 번")
    void approveByPaying() {
        String bought = addToCart(child(ACCOUNT_ID, BUYER_ID), "1", 2);
        String left = addToCart(child(ACCOUNT_ID, BUYER_ID), "2", 1);
        String requestId = sendOne(bought, left);
        Long orderId = order(Map.of("optionId", "1", "childId", "1", "quantity", 2, "unitPrice", PRICE,
            "cartItemId", bought));

        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId).headers(TestMembers.buyer(BUYER_ID)).exchange()
            .expectBody().jsonPath("$.status").isEqualTo("REQUESTED");
        pay(orderId);

        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId).headers(child(ACCOUNT_ID, BUYER_ID)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("PAID")
            .jsonPath("$.payerType").isEqualTo("MEMBER")
            .jsonPath("$.payerId").isEqualTo(String.valueOf(BUYER_ID))
            .jsonPath("$.paidAt").isNotEmpty()
            .jsonPath("$.items[0].purchased").isEqualTo(true)
            .jsonPath("$.items[1].purchased").isEqualTo(false)
            .jsonPath("$.purchases.length()").isEqualTo(1)
            .jsonPath("$.purchases[0].orderId").isEqualTo(String.valueOf(orderId))
            .jsonPath("$.purchases[0].cartItemId").isEqualTo(bought)
            .jsonPath("$.purchases[0].quantity").isEqualTo(2);
        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId + "/timeline").headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectBody()
            .jsonPath("$.steps[1].step").isEqualTo("PAID")
            .jsonPath("$.steps[1].status").isEqualTo("DONE")
            .jsonPath("$.orderIds[0]").isEqualTo(String.valueOf(orderId));
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item where id = ?", Long.class,
            Long.valueOf(bought))).isZero();
        assertThat(count("orders.cart_reservation")).isZero();
        client.delete().uri(CART_ITEMS_PATH + "/" + left).headers(child(ACCOUNT_ID, BUYER_ID)).exchange()
            .expectStatus().isNoContent();
        List<JsonNode> paid = outbox("wish.paid");
        assertThat(paid).singleElement().satisfies(event -> {
            assertThat(event.get("correlationId").asString()).isEqualTo("1");
            assertThat(event.get("data").get("orderId").asLong()).isEqualTo(orderId);
            assertThat(event.get("data").get("payerId").asLong()).isEqualTo(BUYER_ID);
            assertThat(event.get("data").get("memberId").asLong()).isEqualTo(BUYER_ID);
        });
    }

    @Test
    @DisplayName("일부 수량만 결제해도 PAID: 칸 수량 3 중 1개를 사면 산 것 수량 1, 자녀 칸은 수량 2로 예약 없이 남고 wish.paid는 한 번")
    void partialQuantityPurchasePays() {
        String line = addToCart(child(ACCOUNT_ID, BUYER_ID), "1", 3);
        String requestId = sendOne(line);
        Long orderId = order(Map.of("optionId", "1", "childId", "1", "quantity", 1, "unitPrice", PRICE,
            "cartItemId", line));

        pay(orderId);

        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId).headers(child(ACCOUNT_ID, BUYER_ID)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("PAID")
            .jsonPath("$.purchases.length()").isEqualTo(1)
            .jsonPath("$.purchases[0].quantity").isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select quantity from orders.wish_purchase where request_id = ?",
            Integer.class, Long.valueOf(requestId))).isEqualTo(1);
        Map<String, Object> cartLine = jdbcTemplate.queryForMap(
            "select quantity, reservation_id from orders.cart_item where id = ?", Long.valueOf(line));
        assertThat(cartLine).containsEntry("quantity", 2);
        assertThat(cartLine.get("reservation_id")).isNull();
        assertThat(count("orders.cart_reservation")).isZero();
        assertThat(outbox("wish.paid")).singleElement()
            .satisfies(event -> assertThat(event.get("data").get("orderId").asLong()).isEqualTo(orderId));
    }

    @Test
    @DisplayName("보호자가 주문한 뒤 결제 전에 철회해도 결제는 막지 않는다: 결제되면 칸은 정리되고 조르기는 철회 상태로 남는다")
    void cancelAfterOrderDoesNotBlockPayment() {
        String line = addToCart(child(ACCOUNT_ID, BUYER_ID), "1", 1);
        String requestId = sendOne(line);
        Long orderId = order(Map.of("optionId", "1", "childId", "1", "quantity", 1, "unitPrice", PRICE,
            "cartItemId", line));
        cancel(child(ACCOUNT_ID, BUYER_ID), requestId).expectStatus().isOk();

        pay(orderId);

        assertThat(orderQueries.getOrder(BUYER_ID, orderId).status()).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.cart_item where id = ?", Long.class,
            Long.valueOf(line))).isZero();
        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId).headers(child(ACCOUNT_ID, BUYER_ID)).exchange()
            .expectBody().jsonPath("$.status").isEqualTo("CANCELLED");
        assertThat(outbox("wish.paid")).isEmpty();
    }

    @Test
    @DisplayName("결제되지 않으면(잔액 부족) 조르기는 대기 그대로이고 칸도 예약된 채다")
    void unpaidStaysRequested() {
        String line = addToCart(child(ACCOUNT_ID, BUYER_ID), "1", 1);
        String requestId = sendOne(line);
        Long orderId = order(Map.of("optionId", "1", "childId", "1", "quantity", 1, "unitPrice", PRICE,
            "cartItemId", line));

        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(orderId)));
        eventDelivery.deliver(PaymentEvents.insufficientBalance(orderQueries.getOrder(BUYER_ID, orderId), BUYER_ID));

        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId).headers(child(ACCOUNT_ID, BUYER_ID)).exchange()
            .expectBody()
            .jsonPath("$.status").isEqualTo("REQUESTED")
            .jsonPath("$.purchases.length()").isEqualTo(0);
        client.get().uri(CART_PATH).headers(child(ACCOUNT_ID, BUYER_ID)).exchange()
            .expectBody().jsonPath("$.items[0].reserved").isEqualTo(true);
        assertThat(outbox("wish.paid")).isEmpty();
    }

    @Test
    @DisplayName("기한 만료(#1008): 대기 중이면 expiresAt = 보낸 시각 + 7일, 기한이 지난 조르기의 철회 · 거절은 409")
    void expiry() {
        String requestId = sendOne(addToCart(child(ACCOUNT_ID, BUYER_ID), "1", 1));
        LocalDateTime sentAt = jdbcTemplate.queryForObject("select created_at from orders.wish_request where id = ?",
            LocalDateTime.class, Long.valueOf(requestId));

        String body = client.get().uri(WISH_REQUESTS_PATH + "/" + requestId).headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class).getResponseBody();
        assertThat(LocalDateTime.parse(jsonMapper.readTree(body).get("expiresAt").asString()))
            .isEqualTo(sentAt.plusDays(7));
        jdbcTemplate.update("update orders.wish_request set created_at = created_at - interval '8 days' where id = ?",
            Long.valueOf(requestId));

        cancel(child(ACCOUNT_ID, BUYER_ID), requestId).expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.title").isEqualTo("기한이 지난 조르기입니다");
        reject(TestMembers.buyer(BUYER_ID), requestId, "NEXT_TIME").expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.title").isEqualTo("기한이 지난 조르기입니다");
    }

    @Test
    @DisplayName("공용 장바구니 · 승인 API는 없다(#999)")
    void removedApis() {
        client.get().uri("/api/v1/wish?childId=1").headers(TestMembers.buyer(BUYER_ID)).exchange()
            .expectStatus().isNotFound();
        client.post().uri("/api/v1/wish/items").headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("childId", "1", "optionId", "1", "quantity", 1)).exchange()
            .expectStatus().isNotFound();
        String requestId = sendOne(addToCart(child(ACCOUNT_ID, BUYER_ID), "1", 1));
        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/approval").headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("checkout", "CART")).exchange()
            .expectStatus().isNotFound();
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

    private String sendOne(String... cartItemIds) {
        return sendOne(child(ACCOUNT_ID, BUYER_ID), cartItemIds);
    }

    private String sendOne(Consumer<HttpHeaders> headers, String... cartItemIds) {
        URI location = send(headers, List.of(cartItemIds), null)
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((WISH_REQUESTS_PATH + "/").length());
    }

    private RestTestClient.ResponseSpec send(Consumer<HttpHeaders> headers, List<String> cartItemIds, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("cartItemIds", cartItemIds);
        body.put("message", message);
        return client.post().uri(WISH_REQUESTS_PATH).headers(headers).body(body).exchange();
    }

    private RestTestClient.ResponseSpec reject(Consumer<HttpHeaders> headers, String requestId, String reason) {
        return client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/rejection").headers(headers)
            .body(Map.of("reason", reason)).exchange();
    }

    private RestTestClient.ResponseSpec cancel(Consumer<HttpHeaders> headers, String requestId) {
        return client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/cancellation").headers(headers).exchange();
    }

    private String addToCart(Consumer<HttpHeaders> headers, String optionId, int quantity) {
        URI location = client.post().uri(CART_ITEMS_PATH).headers(headers)
            .body(Map.of("optionId", optionId, "quantity", quantity)).exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((CART_ITEMS_PATH + "/").length());
    }

    private String latestRequestId() {
        return String.valueOf(jdbcTemplate.queryForObject("select max(id) from orders.wish_request", Long.class));
    }

    private List<JsonNode> outbox(String eventType) {
        return jdbcTemplate.queryForList("select payload from orders.order_outbox where event_type = ? order by id",
                String.class, eventType).stream()
            .map(jsonMapper::readTree)
            .toList();
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private void account(Long accountId, Long childProfileId, Long guardianId) {
        childAccountEventListener.onChildAccountChanged(new ChildAccountChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new ChildAccountChangedEvent.Data(accountId, childProfileId, guardianId, false)));
    }

    private static ReplicaChild.Values childReplica(Long childId, Long memberId) {
        return new ReplicaChild.Values(childId, memberId, null, new BigDecimal("100.0"), new BigDecimal("15.0"),
            "NORMAL", false, OCCURRED_AT);
    }

    private static Consumer<HttpHeaders> child(Long accountId, Long guardianId) {
        return headers -> {
            headers.set(PrincipalHeaders.CHILD_ACCOUNT_ID, String.valueOf(accountId));
            headers.set(PrincipalHeaders.CHILD_GUARDIAN_ID, String.valueOf(guardianId));
        };
    }
}
