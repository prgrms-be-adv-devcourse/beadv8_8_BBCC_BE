package com.bbcc.kidly.boundedcontext.wish.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.wish.in.web.WishController.WISH_ITEMS_PATH;
import static com.bbcc.kidly.boundedcontext.wish.in.web.WishController.WISH_PATH;
import static com.bbcc.kidly.boundedcontext.wish.in.web.WishRequestController.WISH_REQUESTS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.cart.in.web.CartController;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.boundedcontext.order.in.event.ChildAccountEventListener;
import com.bbcc.kidly.boundedcontext.order.in.web.OrderController;
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
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 자녀 로그인으로 조르기 쓰기(#906): Gateway가 붙인 자녀 헤더(X-Child-Account-Id · X-Child-Guardian-Id)로 자기 프로필의 공용 장바구니에
 * 담고 · 보고 · 빼고 · 조르기를 보내고 · 내 조르기를 본다. 승인 · 거절은 보호자만. 자녀 계정 → 프로필은 주문이 받아 둔 복사본으로 찾는다.
 */
@FunctionalTest
class WishChildApiTest {

    private static final Long CHILD_ID = 1L;
    private static final Long SIBLING_ID = 2L;
    private static final Long ACCOUNT_ID = 100L;
    private static final Long SIBLING_ACCOUNT_ID = 101L;
    private static final Long DELETED_ACCOUNT_ID = 102L;

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

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        sellerReplicaRepository.save(ReplicaSeller.create(
            new ReplicaSeller.Values(SELLER_ID, "키들리 상점", "ACTIVE", LocalDateTime.now())));
        // 자녀 1 · 2의 보호자는 BUYER_ID
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(new ReplicaChild.Values(SIBLING_ID, BUYER_ID, null,
            new BigDecimal("100.0"), new BigDecimal("15.0"), "NORMAL", false, OCCURRED_AT)));
        account(ACCOUNT_ID, CHILD_ID, false);
        account(SIBLING_ACCOUNT_ID, SIBLING_ID, false);
        account(DELETED_ACCOUNT_ID, CHILD_ID, false);
        account(DELETED_ACCOUNT_ID, CHILD_ID, true);
    }

    @Test
    @DisplayName("자녀가 담기 → 보기 → 조르기 보내기 → 내 조르기 보기, 보호자가 승인. 담은 사람 · 보낸 사람은 자녀다")
    void childFlow() {
        String wishItemId = childAdds(child(ACCOUNT_ID, BUYER_ID));

        client.get().uri(WISH_PATH + "?childId=" + CHILD_ID)
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].wishItemId").isEqualTo(wishItemId)
            .jsonPath("$.items[0].addedBy").isEqualTo("CHILD");
        URI location = client.post().uri(WISH_REQUESTS_PATH)
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .body(Map.of("childId", "1", "wishItemIds", List.of(wishItemId), "message", "사 주세요"))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.requestedBy").isEqualTo("CHILD")
            .returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        String requestId = location.getPath().substring((WISH_REQUESTS_PATH + "/").length());

        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/approval")
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("checkout", "CART", "items", List.of(Map.of("wishItemId", wishItemId, "quantity", 1))))
            .exchange()
            .expectStatus().isOk();

        client.get().uri(WISH_REQUESTS_PATH + "?childId=" + CHILD_ID)
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].requestId").isEqualTo(requestId)
            .jsonPath("$[0].status").isEqualTo("APPROVED");
        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId + "/timeline")
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("자녀는 자기가 담은 줄을 뺀다(204)")
    void childRemovesOwnLine() {
        String wishItemId = childAdds(child(ACCOUNT_ID, BUYER_ID));

        client.delete().uri(WISH_ITEMS_PATH + "/" + wishItemId)
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .exchange()
            .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("자녀는 조르기를 승인 · 거절할 수 없다(403)")
    void childCannotDecide() {
        String wishItemId = childAdds(child(ACCOUNT_ID, BUYER_ID));
        String requestId = client.post().uri(WISH_REQUESTS_PATH)
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .body(Map.of("childId", "1", "wishItemIds", List.of(wishItemId)))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation().getPath()
            .substring((WISH_REQUESTS_PATH + "/").length());

        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/approval")
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .body(Map.of("checkout", "CART", "items", List.of(Map.of("wishItemId", wishItemId, "quantity", 1))))
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.title").isEqualTo("조르기를 처리할 수 없습니다");
        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/rejection")
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .body(Map.of("reason", "OTHER"))
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("자녀가 다른 자녀(형제)의 공용 장바구니 · 조르기를 다루면 403")
    void otherChildForbidden() {
        String siblingItemId = childAdds(child(SIBLING_ACCOUNT_ID, BUYER_ID), SIBLING_ID);

        client.get().uri(WISH_PATH + "?childId=" + SIBLING_ID)
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.title").isEqualTo("다른 자녀의 조르기입니다");
        client.post().uri(WISH_ITEMS_PATH)
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .body(Map.of("childId", String.valueOf(SIBLING_ID), "optionId", "1", "quantity", 1))
            .exchange()
            .expectStatus().isForbidden();
        client.delete().uri(WISH_ITEMS_PATH + "/" + siblingItemId)
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .exchange()
            .expectStatus().isForbidden();
        client.get().uri(WISH_REQUESTS_PATH + "?childId=" + SIBLING_ID)
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .exchange()
            .expectStatus().isForbidden();

        String siblingRequestId = send(child(SIBLING_ACCOUNT_ID, BUYER_ID), SIBLING_ID, siblingItemId);
        client.get().uri(WISH_REQUESTS_PATH + "/" + siblingRequestId)
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.title").isEqualTo("다른 자녀의 조르기입니다");
        client.get().uri(WISH_REQUESTS_PATH + "/" + siblingRequestId + "/timeline")
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .exchange()
            .expectStatus().isForbidden();
        client.post().uri(WISH_REQUESTS_PATH + "/" + siblingRequestId + "/approval")
            .headers(child(ACCOUNT_ID, BUYER_ID))
            .body(Map.of("checkout", "CART", "items", List.of(Map.of("wishItemId", siblingItemId, "quantity", 1))))
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.title").isEqualTo("다른 자녀의 조르기입니다");
    }

    @Test
    @DisplayName("회원 헤더와 자녀 헤더를 함께 보내면 부모로 동작한다(보호자가 승인할 수 있다)")
    void memberHeaderWins() {
        String wishItemId = childAdds(child(ACCOUNT_ID, BUYER_ID));
        String requestId = send(child(ACCOUNT_ID, BUYER_ID), CHILD_ID, wishItemId);
        Consumer<HttpHeaders> both = TestMembers.buyer(BUYER_ID).andThen(child(ACCOUNT_ID, BUYER_ID));

        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/approval")
            .headers(both)
            .body(Map.of("checkout", "CART", "items", List.of(Map.of("wishItemId", wishItemId, "quantity", 1))))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("계정 연결이 없거나 · 삭제됐거나 · 보호자가 헤더와 다르면 403")
    void accountMappingForbidden() {
        for (Consumer<HttpHeaders> headers : List.of(child(999L, BUYER_ID), child(DELETED_ACCOUNT_ID, BUYER_ID),
            child(ACCOUNT_ID, BUYER_ID + 1))) {
            client.get().uri(WISH_PATH + "?childId=" + CHILD_ID)
                .headers(headers)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.title").isEqualTo("조르기를 쓸 수 없는 자녀 계정입니다");
        }
    }

    @Test
    @DisplayName("헤더가 없거나 · 자녀 헤더가 하나뿐이거나 · 형식이 잘못되면 401")
    void unauthorized() {
        List<Consumer<HttpHeaders>> cases = List.of(
            headers -> { },
            headers -> headers.set(PrincipalHeaders.CHILD_ACCOUNT_ID, String.valueOf(ACCOUNT_ID)),
            headers -> headers.set(PrincipalHeaders.CHILD_GUARDIAN_ID, String.valueOf(BUYER_ID)),
            child(ACCOUNT_ID, -1L),
            headers -> {
                headers.set(PrincipalHeaders.CHILD_ACCOUNT_ID, "abc");
                headers.set(PrincipalHeaders.CHILD_GUARDIAN_ID, String.valueOf(BUYER_ID));
            });
        for (Consumer<HttpHeaders> headers : cases) {
            client.get().uri(WISH_PATH + "?childId=" + CHILD_ID)
                .headers(headers)
                .exchange()
                .expectStatus().isUnauthorized();
        }
    }

    @Test
    @DisplayName("자녀 헤더만으로는 주문 · 장바구니 · 취소 API를 부를 수 없다(401, 회원 로그인만 받음)")
    void otherApisRejectChild() {
        Consumer<HttpHeaders> child = child(ACCOUNT_ID, BUYER_ID);

        client.get().uri(OrderController.ORDERS_PATH).headers(child).exchange().expectStatus().isUnauthorized();
        client.post().uri(OrderController.ORDERS_PATH).headers(child)
            .body(Map.of("items", List.of(Map.of("optionId", "1", "quantity", 1, "unitPrice", 25_000)),
                "shippingAddress", Map.of("recipientName", "a", "phone", "010", "zipCode", "1", "address", "b")))
            .exchange().expectStatus().isUnauthorized();
        client.post().uri(OrderController.ORDERS_PATH + "/1/cancel").headers(child)
            .exchange().expectStatus().isUnauthorized();
        client.get().uri(CartController.CART_PATH).headers(child).exchange().expectStatus().isUnauthorized();
        client.post().uri(CartController.CART_ITEMS_PATH).headers(child)
            .body(Map.of("optionId", "1", "quantity", 1))
            .exchange().expectStatus().isUnauthorized();
    }

    private String childAdds(Consumer<HttpHeaders> headers) {
        return childAdds(headers, CHILD_ID);
    }

    private String childAdds(Consumer<HttpHeaders> headers, Long childId) {
        URI location = client.post().uri(WISH_ITEMS_PATH)
            .headers(headers)
            .body(Map.of("childId", String.valueOf(childId), "optionId", "1", "quantity", 1))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.addedBy").isEqualTo("CHILD")
            .returnResult()
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

    private void account(Long accountId, Long childProfileId, boolean deleted) {
        childAccountEventListener.onChildAccountChanged(new ChildAccountChangedEvent(UUID.randomUUID(),
            OCCURRED_AT.plusMinutes(deleted ? 1 : 0), new ChildAccountChangedEvent.Data(accountId, childProfileId,
            BUYER_ID, deleted)));
    }

    private static Consumer<HttpHeaders> child(Long accountId, Long guardianId) {
        return headers -> {
            headers.set(PrincipalHeaders.CHILD_ACCOUNT_ID, String.valueOf(accountId));
            headers.set(PrincipalHeaders.CHILD_GUARDIAN_ID, String.valueOf(guardianId));
        };
    }
}
