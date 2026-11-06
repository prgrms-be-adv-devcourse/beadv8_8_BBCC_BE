package com.bbcc.kidly.boundedcontext.wish.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.wish.in.web.WishController.WISH_ITEMS_PATH;
import static com.bbcc.kidly.boundedcontext.wish.in.web.WishRequestController.WISH_REQUESTS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.boundedcontext.order.in.event.ChildAccountEventListener;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
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
 * 조르기 철회(#968): POST /api/v1/wish/requests/{requestId}/cancellation. 보낸 사람만(자녀가 보냈으면 그 자녀, 보호자가 대신
 * 보냈으면 그 보호자), 처리 전(REQUESTED)에만. 줄은 공용 장바구니에 남고 잠금만 풀린다. 성공하면 wish.cancelled 한 건.
 */
@FunctionalTest
class WishCancelApiTest {

    private static final Long CHILD_ID = 1L;
    private static final Long SIBLING_ID = 2L;
    private static final Long ACCOUNT_ID = 100L;
    private static final Long SIBLING_ACCOUNT_ID = 101L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private SellerReplicaRepository sellerReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private ChildAccountEventListener childAccountEventListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        sellerReplicaRepository.save(ReplicaSeller.create(
            new ReplicaSeller.Values(SELLER_ID, "키들리 상점", "ACTIVE", LocalDateTime.now())));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(new ReplicaChild.Values(SIBLING_ID, BUYER_ID, null,
            new BigDecimal("100.0"), new BigDecimal("15.0"), "NORMAL", false, OCCURRED_AT)));
        account(ACCOUNT_ID, CHILD_ID);
        account(SIBLING_ACCOUNT_ID, SIBLING_ID);
    }

    @Test
    @DisplayName("자녀가 보낸 조르기를 그 자녀가 철회하면 200 CANCELLED · cancelledAt, 줄은 남고 잠금만 풀려 다시 조를 수 있다. "
        + "wish.cancelled 한 건(키 자녀 ID · 조르기 ID · 보호자 · items)")
    void childCancelsOwn() {
        String wishItemId = adds(child(ACCOUNT_ID), CHILD_ID);
        String requestId = send(child(ACCOUNT_ID), CHILD_ID, wishItemId);
        jdbcTemplate.update("delete from orders.order_outbox");

        cancel(child(ACCOUNT_ID), requestId)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.requestId").isEqualTo(requestId)
            .jsonPath("$.status").isEqualTo("CANCELLED")
            .jsonPath("$.requestedBy").isEqualTo("CHILD")
            .jsonPath("$.cancelledAt").isNotEmpty()
            .jsonPath("$.decidedAt").doesNotExist()
            .jsonPath("$.items[0].wishItemId").isEqualTo(wishItemId);

        assertThat(jdbcTemplate.queryForMap("select status, request_id from orders.wish_item where id = ?",
            Long.valueOf(wishItemId))).containsEntry("status", "ACTIVE").containsEntry("request_id", null);
        Map<String, Object> row = jdbcTemplate.queryForMap("select * from orders.order_outbox");
        assertThat(row).containsEntry("event_type", "wish.cancelled")
            .containsEntry("topic", "kidly.order.events")
            .containsEntry("message_key", String.valueOf(CHILD_ID));
        JsonNode payload = jsonMapper.readTree((String) row.get("payload"));
        assertThat(payload.get("aggregateId").asString()).isEqualTo(requestId);
        JsonNode data = payload.get("data");
        assertThat(data.propertyNames()).containsExactly("wishRequestId", "childId", "memberId", "items");
        assertThat(data.get("wishRequestId").asLong()).isEqualTo(Long.parseLong(requestId));
        assertThat(data.get("childId").asLong()).isEqualTo(CHILD_ID);
        assertThat(data.get("memberId").asLong()).isEqualTo(BUYER_ID);
        assertThat(data.get("items").get(0).get("optionId").asLong()).isEqualTo(1L);
        assertThat(data.get("items").get(0).get("productId").asLong()).isEqualTo(1L);
        assertThat(data.get("items").get(0).get("quantity").asInt()).isEqualTo(1);

        send(child(ACCOUNT_ID), CHILD_ID, wishItemId);
    }

    @Test
    @DisplayName("타임라인: SENT 다음 CANCELLED(DONE, 철회 시각), 상태 CANCELLED · cancelledBy 보낸 사람 종류")
    void timeline() {
        String requestId = send(child(ACCOUNT_ID), CHILD_ID, adds(child(ACCOUNT_ID), CHILD_ID));
        cancel(child(ACCOUNT_ID), requestId).expectStatus().isOk();

        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId + "/timeline")
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")
            .jsonPath("$.cancelledBy").isEqualTo("CHILD")
            .jsonPath("$.steps.length()").isEqualTo(2)
            .jsonPath("$.steps[0].step").isEqualTo("SENT")
            .jsonPath("$.steps[1].step").isEqualTo("CANCELLED")
            .jsonPath("$.steps[1].status").isEqualTo("DONE")
            .jsonPath("$.steps[1].at").isNotEmpty();
    }

    @Test
    @DisplayName("보호자가 대신 보낸 조르기는 그 보호자가 철회할 수 있다(200). 자녀는 403")
    void parentCancelsOwn() {
        String requestId = send(TestMembers.buyer(BUYER_ID), CHILD_ID, adds(TestMembers.buyer(BUYER_ID), CHILD_ID));

        cancel(child(ACCOUNT_ID), requestId)
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.title").isEqualTo("조르기를 철회할 수 없습니다");
        cancel(TestMembers.buyer(BUYER_ID), requestId)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")
            .jsonPath("$.requestedBy").isEqualTo("PARENT");
    }

    @Test
    @DisplayName("남의 것은 철회할 수 없고 아무것도 바뀌지 않는다: 보낸 사람이 아닌 보호자 403 · 다른 자녀(형제) 403 · 다른 회원 404")
    void othersCannotCancel() {
        String requestId = send(child(ACCOUNT_ID), CHILD_ID, adds(child(ACCOUNT_ID), CHILD_ID));
        jdbcTemplate.update("delete from orders.order_outbox");

        cancel(TestMembers.buyer(BUYER_ID), requestId)
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.title").isEqualTo("조르기를 철회할 수 없습니다");
        cancel(child(SIBLING_ACCOUNT_ID), requestId)
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.title").isEqualTo("다른 자녀의 조르기입니다");
        cancel(TestMembers.buyer(BUYER_ID + 50), requestId).expectStatus().isNotFound();

        assertThat(jdbcTemplate.queryForObject("select status from orders.wish_request where id = ?", String.class,
            Long.valueOf(requestId))).isEqualTo("REQUESTED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox", Long.class)).isZero();
    }

    @Test
    @DisplayName("처리된 조르기(승인 · 거절 · 철회)는 철회하면 409 ALREADY_DECIDED이고 이벤트가 없다")
    void processedIsConflict() {
        String approvedItem = adds(child(ACCOUNT_ID), CHILD_ID);
        String approved = send(child(ACCOUNT_ID), CHILD_ID, approvedItem);
        client.post().uri(WISH_REQUESTS_PATH + "/" + approved + "/approval")
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("checkout", "CART", "items", List.of(Map.of("wishItemId", approvedItem, "quantity", 1))))
            .exchange()
            .expectStatus().isOk();
        String rejected = send(child(ACCOUNT_ID), CHILD_ID, approvedItem);
        client.post().uri(WISH_REQUESTS_PATH + "/" + rejected + "/rejection")
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("reason", "OTHER"))
            .exchange()
            .expectStatus().isOk();
        String cancelled = send(child(ACCOUNT_ID), CHILD_ID, approvedItem);
        cancel(child(ACCOUNT_ID), cancelled).expectStatus().isOk();
        jdbcTemplate.update("delete from orders.order_outbox");

        for (String requestId : List.of(approved, rejected, cancelled)) {
            cancel(child(ACCOUNT_ID), requestId)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.title").isEqualTo("이미 처리한 조르기입니다");
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox", Long.class)).isZero();
    }

    @Test
    @DisplayName("로그인하지 않으면 401, 없는 조르기는 404")
    void unauthorizedAndNotFound() {
        client.post().uri(WISH_REQUESTS_PATH + "/1/cancellation").exchange().expectStatus().isUnauthorized();
        cancel(child(ACCOUNT_ID), "999999").expectStatus().isNotFound();
    }

    private RestTestClient.ResponseSpec cancel(Consumer<HttpHeaders> headers, String requestId) {
        return client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/cancellation")
            .headers(headers)
            .exchange();
    }

    private String adds(Consumer<HttpHeaders> headers, Long childId) {
        URI location = client.post().uri(WISH_ITEMS_PATH)
            .headers(headers)
            .body(Map.of("childId", String.valueOf(childId), "optionId", "1", "quantity", 1))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class)
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((WISH_ITEMS_PATH + "/").length());
    }

    private String send(Consumer<HttpHeaders> headers, Long childId, String wishItemId) {
        URI location = client.post().uri(WISH_REQUESTS_PATH)
            .headers(headers)
            .body(Map.of("childId", String.valueOf(childId), "wishItemIds", List.of(wishItemId)))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class)
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((WISH_REQUESTS_PATH + "/").length());
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
