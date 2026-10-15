package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderDeliveredAtSetter;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

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
    private EventPublisher eventPublisher;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderDeliveredAtSetter deliveredAtSetter;

    @Autowired
    private TransactionTemplate transactionTemplate;

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

        @Test
        @DisplayName("단가가 음수(-1)면 400")
        void negativeUnitPrice() {
            client.post().uri(ORDERS_PATH)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .body(orderBody("1", null, 1, -1))
                .exchange()
                .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("단가 0은 형식 검증을 통과한다(가격이 다르면 409)")
        void zeroUnitPricePassesValidation() {
            client.post().uri(ORDERS_PATH)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .body(orderBody("1", null, 1, 0))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.title").isEqualTo("가격이 바뀌었습니다");
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

        @ParameterizedTest(name = "size={0}")
        @ValueSource(ints = {1, 100})
        @DisplayName("목록 size 1과 100은 200")
        void pageSizeBoundsAreAllowed(int size) {
            client.get().uri(ORDERS_PATH + "?size=" + size)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.size").isEqualTo(size);
        }

        @Test
        @DisplayName("목록 size가 0이면 400")
        void zeroPageSize() {
            client.get().uri(ORDERS_PATH + "?size=0")
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .exchange()
                .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("목록 page가 음수(-1)면 400")
        void negativePage() {
            client.get().uri(ORDERS_PATH + "?page=-1")
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .exchange()
                .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("결제 실패로 취소된 주문은 상세에 CANCELLED와 취소 사유가 보인다")
        void cancelledOrder() {
            String orderId = placeAndReadId();
            transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(new PaymentFailedEvent(
                UUID.randomUUID(), LocalDateTime.now(), new PaymentFailedEvent.Data(500L, Long.valueOf(orderId),
                    BUYER_ID, 25_000L, PaymentFailureCode.INSUFFICIENT_BALANCE, LocalDateTime.now()))));

            client.get().uri(ORDERS_PATH + "/" + orderId)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED")
                .jsonPath("$.cancelReason").isEqualTo("PAYMENT_FAILED");
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

    @Nested
    @DisplayName("구매확정")
    class Confirm {

        @Test
        @DisplayName("배송 완료된 주문상품을 확정하면 200, 다시 눌러도 200이고 주문 상세에 확정이 보인다")
        void confirmsTwice() {
            String orderId = placeAndReadId();
            markDelivered(orderId);
            String orderItemId = orderItemIdOf(orderId);

            confirm(orderId, orderItemId, BUYER_ID)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.orderItemId").isEqualTo(orderItemId)
                .jsonPath("$.status").isEqualTo("CONFIRMED")
                .jsonPath("$.confirmType").isEqualTo("DIRECT")
                .jsonPath("$.confirmedAt").exists();
            confirm(orderId, orderItemId, BUYER_ID)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMED");

            client.get().uri(ORDERS_PATH + "/" + orderId)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].status").isEqualTo("CONFIRMED")
                .jsonPath("$.items[0].confirmType").isEqualTo("DIRECT")
                .jsonPath("$.items[0].confirmedAt").exists();
        }

        @Test
        @DisplayName("배송 전이면 409 NOT_DELIVERED")
        void notDelivered() {
            String orderId = placeAndReadId();
            String orderItemId = orderItemIdOf(orderId);

            confirm(orderId, orderItemId, BUYER_ID)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.title").isEqualTo("배송 완료 전입니다");
        }

        @Test
        @DisplayName("남의 주문이면 404 ORDER_NOT_FOUND")
        void othersOrder() {
            String orderId = placeAndReadId();
            markDelivered(orderId);
            String orderItemId = orderItemIdOf(orderId);

            confirm(orderId, orderItemId, OTHER_ID)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("주문을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("주문에 없는 주문상품이면 404 ORDER_ITEM_NOT_FOUND")
        void missingItem() {
            String orderId = placeAndReadId();

            confirm(orderId, "999999", BUYER_ID)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("주문상품을 찾을 수 없습니다");
        }

        private RestTestClient.ResponseSpec confirm(String orderId, String orderItemId, Long memberId) {
            return client.post().uri(ORDERS_PATH + "/" + orderId + "/items/" + orderItemId + "/confirm")
                .headers(headers -> headers.setBearerAuth(tokens.buyer(memberId)))
                .exchange();
        }

        /**
         * 이벤트 대신 서비스를 바로 불러 결제완료 · 판매자 배송 완료(지금 시각)로 만든다.
         */
        private void markDelivered(String orderId) {
            orderService.markPaid(Long.valueOf(orderId), 500L,
                orderService.getOrder(BUYER_ID, Long.valueOf(orderId)).totalAmount());
            deliveredAtSetter.recordDelivered(Long.valueOf(orderId), SELLER_ID, LocalDateTime.now());
        }

        private String orderItemIdOf(String orderId) {
            OrderItems detail = client.get().uri(ORDERS_PATH + "/" + orderId)
                .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderItems.class)
                .returnResult()
                .getResponseBody();
            assertThat(detail).isNotNull();
            return detail.items().get(0).orderItemId();
        }
    }

    private record OrderItems(List<OrderItemRow> items) {
    }

    private record OrderItemRow(String orderItemId) {
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
