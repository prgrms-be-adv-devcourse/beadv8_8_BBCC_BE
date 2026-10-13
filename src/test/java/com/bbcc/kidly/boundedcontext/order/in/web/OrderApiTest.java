package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class OrderApiTest {

    private static final Long OTHER_ID = 3L;
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Nested
    @DisplayName("주문하기")
    class Place {

        @Test
        @DisplayName("201 + Location과 결제대기 주문 상세를 돌려준다")
        void placesOrder() {
            URI location = client.post().uri(ORDERS_PATH)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .body(orderBody("1", "1", 2, 25_000))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", ORDERS_PATH + "/\\d+")
                .expectBody()
                .jsonPath("$.status").isEqualTo("PENDING_PAYMENT")
                .jsonPath("$.totalAmount").isEqualTo(50_000)
                .jsonPath("$.paymentDueAt").exists()
                .jsonPath("$.shippingAddress.recipientName").isEqualTo("홍길동")
                .jsonPath("$.items[0].optionId").isEqualTo("1")
                .jsonPath("$.items[0].sellerId").isEqualTo("2")
                .jsonPath("$.items[0].amount").isEqualTo(50_000)
                .jsonPath("$.items[0].child.childId").isEqualTo("1")
                .returnResult()
                .getResponseHeaders().getLocation();

            assertThat(location).isNotNull();
        }

        @Test
        @DisplayName("주문상품이 비어 있으면 400")
        void emptyItems() {
            client.post().uri(ORDERS_PATH)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .body(Map.of("items", List.of(), "shippingAddress", ADDRESS))
                .exchange()
                .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("옵션 ID가 숫자가 아니면 400")
        void invalidOptionId() {
            client.post().uri(ORDERS_PATH)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .body(orderBody("abc", null, 1, 25_000))
                .exchange()
                .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("배송지가 비어 있으면 400")
        void blankAddress() {
            client.post().uri(ORDERS_PATH)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .body(Map.of(
                    "items", List.of(Map.of("optionId", "1", "quantity", 1, "unitPrice", 25_000)),
                    "shippingAddress", Map.of("recipientName", "", "phone", "", "zipCode", "", "address", "")))
                .exchange()
                .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("주문 조회")
    class GetOrders {

        @Test
        @DisplayName("본인 주문의 상세를 돌려준다")
        void getOrder() {
            String orderId = placeAndReadId();

            client.get().uri(ORDERS_PATH + "/" + orderId)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.orderId").isEqualTo(orderId)
                .jsonPath("$.items.length()").isEqualTo(1);
        }

        @Test
        @DisplayName("목록은 6.1 목록 형식으로 최근 주문부터 돌려준다")
        void getOrderPage() {
            String first = placeAndReadId();
            String second = placeAndReadId();

            client.get().uri(ORDERS_PATH)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].orderId").isEqualTo(second)
                .jsonPath("$.content[1].orderId").isEqualTo(first)
                .jsonPath("$.content[0].status").isEqualTo("PENDING_PAYMENT")
                .jsonPath("$.content[0].totalAmount").isEqualTo(25_000)
                .jsonPath("$.content[0].orderedAt").exists()
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(20)
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.totalPages").isEqualTo(1)
                .jsonPath("$.hasNext").isEqualTo(false);
        }

        @Test
        @DisplayName("목록 size가 100을 넘으면 400")
        void tooLargePageSize() {
            client.get().uri(ORDERS_PATH + "?size=101")
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .exchange()
                .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("남의 주문이면 404 ORDER_NOT_FOUND")
        void othersOrderIsNotFound() {
            String orderId = placeAndReadId();

            client.get().uri(ORDERS_PATH + "/" + orderId)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(OTHER_ID)))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("주문을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("로그인하지 않으면 401")
        void unauthorized() {
            client.get().uri(ORDERS_PATH)
                .exchange()
                .expectStatus().isUnauthorized();
        }
    }

    private String placeAndReadId() {
        URI location = client.post().uri(ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .body(orderBody("1", null, 1, 25_000))
            .exchange()
            .expectStatus().isCreated()
            .expectBody().returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((ORDERS_PATH + "/").length());
    }

    private static Map<String, Object> orderBody(String optionId, String childId, int quantity, long unitPrice) {
        Map<String, Object> item = childId == null
            ? Map.of("optionId", optionId, "quantity", quantity, "unitPrice", unitPrice)
            : Map.of("optionId", optionId, "childId", childId, "quantity", quantity, "unitPrice", unitPrice);
        return Map.of("items", List.of(item), "shippingAddress", ADDRESS);
    }
}
