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
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 조르기 API(#701): 보내기 · 목록 · 한 건 · 승인(바로 결제 · 장바구니로 옮기기) · 거절. 응답 칸 · 상태 코드 · 권한을 본다.
 * 규칙 경계 · 결제 결과 반영은 WishRequestIntegrationTest, 동시 처리는 WishRequestConcurrencyIntegrationTest가 본다.
 */
@FunctionalTest
class WishRequestApiTest {

    private static final Long OTHER_PARENT_ID = 3L;
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "보호자", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시");

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private SellerReplicaRepository sellerReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    private String wishItemId;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        sellerReplicaRepository.save(ReplicaSeller.create(
            new ReplicaSeller.Values(SELLER_ID, "키들리 상점", "ACTIVE", LocalDateTime.now())));
        // 자녀 1의 보호자는 BUYER_ID
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        URI location = client.post().uri(WISH_ITEMS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("childId", "1", "optionId", "1", "quantity", 2))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class)
            .getResponseHeaders().getLocation();
        wishItemId = location.getPath().substring((WISH_ITEMS_PATH + "/").length());
    }

    @Test
    @DisplayName("보내면 201 + Location이고, 목록 · 한 건 · 공용 장바구니 줄에서 진행 중인 조르기로 보인다")
    void send() {
        String requestId = sendAndReadId();

        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.requestId").isEqualTo(requestId)
            .jsonPath("$.childId").isEqualTo("1")
            .jsonPath("$.status").isEqualTo("REQUESTED")
            .jsonPath("$.requestedBy").isEqualTo("PARENT")
            .jsonPath("$.message").isEqualTo("이거 사 주세요")
            .jsonPath("$.requestedAt").isNotEmpty()
            .jsonPath("$.checkout").doesNotExist()
            .jsonPath("$.items[0].wishItemId").isEqualTo(wishItemId)
            .jsonPath("$.items[0].productName").isEqualTo("상품 A")
            .jsonPath("$.items[0].sellerName").isEqualTo("키들리 상점")
            .jsonPath("$.items[0].requestedQuantity").isEqualTo(2)
            .jsonPath("$.items[0].unitPrice").isEqualTo(25_000)
            .jsonPath("$.items[0].saleStatus").isEqualTo("ON_SALE")
            .jsonPath("$.items[0].purchasable").isEqualTo(true);
        client.get().uri(WISH_REQUESTS_PATH + "?childId=1")
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].requestId").isEqualTo(requestId);
        client.get().uri(WISH_PATH + "?childId=1")
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectBody()
            .jsonPath("$.items[0].requestId").isEqualTo(requestId);
    }

    @Test
    @DisplayName("진행 중인 조르기에 든 줄을 다시 보내거나 빼면 409")
    void lockedLine() {
        sendAndReadId();

        client.post().uri(WISH_REQUESTS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("childId", "1", "wishItemIds", List.of(wishItemId)))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("조르는 중인 상품입니다");
        client.delete().uri(WISH_ITEMS_PATH + "/" + wishItemId)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("바로 결제로 승인하면 200이고 주문 ID가 기록된다")
    void approveOrder() {
        String requestId = sendAndReadId();

        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/approval")
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("checkout", "ORDER",
                "items", List.of(Map.of("wishItemId", wishItemId, "quantity", 1, "unitPrice", 25_000)),
                "shippingAddress", ADDRESS))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("APPROVED")
            .jsonPath("$.checkout").isEqualTo("ORDER")
            .jsonPath("$.orderId").isNotEmpty()
            .jsonPath("$.orderResult").doesNotExist()
            .jsonPath("$.decidedAt").isNotEmpty()
            .jsonPath("$.items[0].approvedQuantity").isEqualTo(1);

        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/rejection")
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("reason", "OTHER"))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("이미 처리한 조르기입니다");
    }

    @Test
    @DisplayName("장바구니로 옮기기로 승인하면 보호자 장바구니에 조른 자녀로 담긴다")
    void approveCart() {
        String requestId = sendAndReadId();

        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/approval")
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("checkout", "CART", "items", List.of(Map.of("wishItemId", wishItemId, "quantity", 3))))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.checkout").isEqualTo("CART")
            .jsonPath("$.orderId").doesNotExist();
        client.get().uri(CartController.CART_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectBody()
            .jsonPath("$.items[0].childId").isEqualTo("1")
            .jsonPath("$.items[0].quantity").isEqualTo(3);
    }

    @Test
    @DisplayName("거절하면 200이고 사유가 남는다. 목록에 없는 사유 · 빈 승인 · 바로 결제 배송지 없음은 400")
    void rejectAndBadRequests() {
        String requestId = sendAndReadId();
        String approval = WISH_REQUESTS_PATH + "/" + requestId + "/approval";

        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/rejection")
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("reason", "BORING"))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("잘못된 거절 사유입니다");
        client.post().uri(approval)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("checkout", "ORDER", "items", List.of()))
            .exchange()
            .expectStatus().isBadRequest();
        client.post().uri(approval)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("checkout", "ORDER",
                "items", List.of(Map.of("wishItemId", wishItemId, "quantity", 1, "unitPrice", 25_000))))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("잘못된 승인입니다");

        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/rejection")
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("reason", "TOO_EXPENSIVE"))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("REJECTED")
            .jsonPath("$.rejectReason").isEqualTo("TOO_EXPENSIVE");
    }

    @Test
    @DisplayName("다른 부모는 남의 자녀 조르기를 보내거나 · 보거나 · 승인 · 거절할 수 없다(404)")
    void otherParent() {
        String requestId = sendAndReadId();

        client.post().uri(WISH_REQUESTS_PATH)
            .headers(TestMembers.buyer(OTHER_PARENT_ID))
            .body(Map.of("childId", "1", "wishItemIds", List.of(wishItemId)))
            .exchange()
            .expectStatus().isNotFound();
        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId)
            .headers(TestMembers.buyer(OTHER_PARENT_ID))
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.title").isEqualTo("조르기를 찾을 수 없습니다");
        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/rejection")
            .headers(TestMembers.buyer(OTHER_PARENT_ID))
            .body(Map.of("reason", "OTHER"))
            .exchange()
            .expectStatus().isNotFound();
        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/approval")
            .headers(TestMembers.buyer(OTHER_PARENT_ID))
            .body(Map.of("checkout", "CART", "items", List.of(Map.of("wishItemId", wishItemId, "quantity", 1))))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("타임라인(#702): 바로 결제로 승인하면 보냄 → 승인 → 주문 단계가 [단계 · 상태 · 시각]으로 이어진다. 남의 자녀면 404")
    void timeline() {
        String requestId = sendAndReadId();
        client.post().uri(WISH_REQUESTS_PATH + "/" + requestId + "/approval")
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("checkout", "ORDER",
                "items", List.of(Map.of("wishItemId", wishItemId, "quantity", 1, "unitPrice", 25_000)),
                "shippingAddress", ADDRESS))
            .exchange()
            .expectStatus().isOk();

        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId + "/timeline")
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.requestId").isEqualTo(requestId)
            .jsonPath("$.status").isEqualTo("APPROVED")
            .jsonPath("$.checkout").isEqualTo("ORDER")
            .jsonPath("$.orderId").isNotEmpty()
            .jsonPath("$.rejectReason").doesNotExist()
            .jsonPath("$.steps.length()").isEqualTo(8)
            .jsonPath("$.steps[0].step").isEqualTo("SENT")
            .jsonPath("$.steps[0].status").isEqualTo("DONE")
            .jsonPath("$.steps[0].at").isNotEmpty()
            .jsonPath("$.steps[1].step").isEqualTo("APPROVED")
            .jsonPath("$.steps[2].step").isEqualTo("CREATED")
            .jsonPath("$.steps[3].step").isEqualTo("PAYMENT_REQUESTED")
            .jsonPath("$.steps[3].status").isEqualTo("WAITING")
            .jsonPath("$.steps[3].at").doesNotExist()
            .jsonPath("$.steps[7].step").isEqualTo("CONFIRMED");
        client.get().uri(WISH_REQUESTS_PATH + "/" + requestId + "/timeline")
            .headers(TestMembers.buyer(OTHER_PARENT_ID))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void unauthorized() {
        client.get().uri(WISH_REQUESTS_PATH + "?childId=1")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private String sendAndReadId() {
        URI location = client.post().uri(WISH_REQUESTS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("childId", "1", "wishItemIds", List.of(wishItemId), "message", "이거 사 주세요"))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class)
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.getPath()).as("Location은 공용 장바구니 아래 조르기 경로(#718)").matches("/api/v1/wish/requests/\\d+");
        return location.getPath().substring((WISH_REQUESTS_PATH + "/").length());
    }
}
