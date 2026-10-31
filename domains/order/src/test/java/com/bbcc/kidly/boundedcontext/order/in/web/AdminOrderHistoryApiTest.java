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
import com.bbcc.kidly.boundedcontext.order.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자 주문 상태 이력 조회(#442): GET /api/v1/admin/orders/{orderId}/history. 감싸지 않은 배열, 오래된 것부터.
 */
@FunctionalTest
class AdminOrderHistoryApiTest {

    private static final Long ADMIN_ID = 99L;
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @Test
    @DisplayName("주문하기 · 구매자 취소 이력이 오래된 것부터 모든 필드(ID는 문자열, 없는 값은 null 키 유지)로 나온다")
    void listsHistoryOldestFirst() {
        String orderId = placeOrder();
        client.post().uri(ORDERS_PATH + "/" + orderId + "/cancel")
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk();

        client.get().uri(historyPath(orderId))
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].historyId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$[0].targetType").isEqualTo("ORDER")
            .jsonPath("$[0].targetId").isEqualTo(orderId)
            .jsonPath("$[0].fromStatus").value(from -> assertThat(from).isNull())
            .jsonPath("$[0].toStatus").isEqualTo("PENDING_PAYMENT")
            .jsonPath("$[0].reason").value(reason -> assertThat(reason).isNull())
            .jsonPath("$[0].actorType").isEqualTo("BUYER")
            .jsonPath("$[0].actorId").isEqualTo(String.valueOf(BUYER_ID))
            .jsonPath("$[0].source").isEqualTo("POST /api/v1/orders")
            .jsonPath("$[0].occurredAt").exists()
            .jsonPath("$[1].fromStatus").isEqualTo("PENDING_PAYMENT")
            .jsonPath("$[1].toStatus").isEqualTo("CANCELLED")
            .jsonPath("$[1].reason").isEqualTo("BUYER_CANCELLED")
            .jsonPath("$[1].source").isEqualTo("POST /api/v1/orders/{orderId}/cancel");
    }

    @Test
    @DisplayName("이벤트가 바꾼 상태는 actorType EVENT · actorId null(키 유지) · source 이벤트 타입")
    void eventActorHasNullId() {
        String orderId = placeOrder();
        orderService.cancelForPaymentFailure(Long.valueOf(orderId));

        client.get().uri(historyPath(orderId))
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[1].actorType").isEqualTo("EVENT")
            .jsonPath("$[1].actorId").value(id -> assertThat(id).isNull())
            .jsonPath("$[1].reason").isEqualTo("PAYMENT_FAILED")
            .jsonPath("$[1].source").isEqualTo("payment.failed");
    }

    @Test
    @DisplayName("주문은 있는데 이력이 없으면(#442 이전 주문) 200 · []")
    void emptyWhenNoHistory() {
        String orderId = placeOrder();
        jdbcTemplate.update("delete from orders.order_status_history where order_id = ?", Long.valueOf(orderId));

        client.get().uri(historyPath(orderId))
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .json("[]");
    }

    @Test
    @DisplayName("없는 주문이면 404 ORDER_NOT_FOUND(ProblemDetail)")
    void notFound() {
        client.get().uri(historyPath("999999"))
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(404)
            .jsonPath("$.title").isEqualTo("주문을 찾을 수 없습니다")
            .jsonPath("$.detail").isEqualTo("없는 주문입니다. 주문 목록에서 다시 선택해 주세요.");
    }

    @Test
    @DisplayName("주문 ID가 숫자가 아니면(abc) 400")
    void nonNumericIdIsBadRequest() {
        client.get().uri(historyPath("abc"))
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("토큰이 없으면 401, 구매자 · 판매자 토큰이면 403")
    void adminOnly() {
        String orderId = placeOrder();

        client.get().uri(historyPath(orderId))
            .exchange()
            .expectStatus().isUnauthorized();
        client.get().uri(historyPath(orderId))
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isForbidden();
        client.get().uri(historyPath(orderId))
            .headers(TestMembers.seller(SELLER_ID))
            .exchange()
            .expectStatus().isForbidden();
    }

    private static String historyPath(String orderId) {
        return ADMIN_ORDERS_PATH + "/" + orderId + "/history";
    }

    private String placeOrder() {
        URI location = client.post().uri(ORDERS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("items", List.of(Map.of("optionId", "1", "quantity", 1, "unitPrice", 25_000)),
                "shippingAddress", ADDRESS))
            .exchange()
            .expectStatus().isCreated()
            .expectBody().returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((ORDERS_PATH + "/").length());
    }
}
