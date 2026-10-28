package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.AdminOrderController.ADMIN_ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminOrderApiTest {

    private static final Long OTHER_BUYER_ID = 3L;
    private static final Long ADMIN_ID = 99L;
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @Test
    @DisplayName("여러 회원의 주문이 주문 ID 내림차순으로 모두 나오고, 취소 주문만 cancelReason이 있다")
    void listsAllMembersOrdersNewestFirst() {
        String first = placeOrder(BUYER_ID, 1);
        String second = placeOrder(OTHER_BUYER_ID, 2);
        String third = placeOrder(BUYER_ID, 1);
        orderService.cancelForPaymentFailure(Long.valueOf(second));

        client.get().uri(ADMIN_ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(3)
            .jsonPath("$.content[0].orderId").isEqualTo(third)
            .jsonPath("$.content[0].memberId").isEqualTo(String.valueOf(BUYER_ID))
            .jsonPath("$.content[0].status").isEqualTo("PENDING_PAYMENT")
            .jsonPath("$.content[0].cancelReason").value(reason -> assertThat(reason).isNull())
            .jsonPath("$.content[0].totalAmount").isEqualTo(25_000)
            .jsonPath("$.content[0].orderedAt").exists()
            .jsonPath("$.content[0].itemCount").isEqualTo(1)
            .jsonPath("$.content[1].orderId").isEqualTo(second)
            .jsonPath("$.content[1].memberId").isEqualTo(String.valueOf(OTHER_BUYER_ID))
            .jsonPath("$.content[1].status").isEqualTo("CANCELLED")
            .jsonPath("$.content[1].cancelReason").isEqualTo("PAYMENT_FAILED")
            .jsonPath("$.content[1].totalAmount").isEqualTo(50_000)
            .jsonPath("$.content[1].itemCount").isEqualTo(1)
            .jsonPath("$.content[2].orderId").isEqualTo(first)
            .jsonPath("$.content[2].cancelReason").value(reason -> assertThat(reason).isNull())
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(20)
            .jsonPath("$.totalElements").isEqualTo(3)
            .jsonPath("$.totalPages").isEqualTo(1)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("주문이 없으면 200 · content []")
    void emptyWhenNoOrders() {
        client.get().uri(ADMIN_ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    @ParameterizedTest(name = "size={0}")
    @ValueSource(ints = {0, 101})
    @DisplayName("size가 1~100 밖(0 · 101)이면 400")
    void sizeOutOfRangeIsBadRequest(int size) {
        client.get().uri(ADMIN_ORDERS_PATH + "?size=" + size)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("page가 음수(-1)면 400")
    void negativePageIsBadRequest() {
        client.get().uri(ADMIN_ORDERS_PATH + "?page=-1")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("offset이 int를 넘는 큰 page(1073741824 · size 2)는 200 · 빈 목록 · 전체 건수 실제 값 · hasNext false")
    void hugePageIsEmpty() {
        placeOrder(BUYER_ID, 1);

        client.get().uri(ADMIN_ORDERS_PATH + "?page=1073741824&size=2")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.page").isEqualTo(1073741824)
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void unauthorized() {
        client.get().uri(ADMIN_ORDERS_PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("구매자 · 판매자 토큰이면 403")
    void forbiddenForNonAdmin() {
        client.get().uri(ADMIN_ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isForbidden();
        client.get().uri(ADMIN_ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .exchange()
            .expectStatus().isForbidden();
    }

    private String placeOrder(Long memberId, int quantity) {
        URI location = client.post().uri(ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(memberId)))
            .body(Map.of("items", List.of(Map.of("optionId", "1", "quantity", quantity, "unitPrice", 25_000)),
                "shippingAddress", ADDRESS))
            .exchange()
            .expectStatus().isCreated()
            .expectBody().returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((ORDERS_PATH + "/").length());
    }
}
