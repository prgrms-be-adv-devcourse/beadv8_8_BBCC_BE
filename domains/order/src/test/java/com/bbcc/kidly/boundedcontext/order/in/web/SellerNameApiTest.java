package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.cart.in.web.CartController.CART_ITEMS_PATH;
import static com.bbcc.kidly.boundedcontext.cart.in.web.CartController.CART_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.MarkPaidCommands;
import com.bbcc.kidly.boundedcontext.order.OrderDeliveredAtSetter;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 판매자 상호명(sellerName) 표시: 장바구니 · 주문 상세 · 주문 목록(대표 주문상품) · 구매확정 응답.
 * 판매자 복사본이 오기 전에는 null, 오면 상호명, 바뀌면 주문 뒤에도 지금 상호명을 따라간다(주문상품에 저장하지 않음).
 */
@FunctionalTest
class SellerNameApiTest {

    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderDeliveredAtSetter deliveredAtSetter;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @Test
    @DisplayName("판매자 이벤트 전에는 세 곳 모두 sellerName null, 이벤트 뒤 상호명, 상호명이 바뀌면 주문 뒤에도 새 이름")
    void sellerNameFollowsReplica() {
        addToCart();
        String orderId = placeOrder();

        assertSellerNames(orderId, null);

        publishSeller(OCCURRED_AT, "키들리샵");
        assertSellerNames(orderId, "키들리샵");

        publishSeller(OCCURRED_AT.plusMinutes(1), "키들리 아동복");
        assertSellerNames(orderId, "키들리 아동복");
    }

    @Test
    @DisplayName("구매확정 응답에도 그 주문상품 판매자의 지금 상호명이 나온다")
    void confirmResponseHasSellerName() {
        String orderId = placeOrder();
        publishSeller(OCCURRED_AT, "키들리샵");
        orderService.markPaid(MarkPaidCommands.matching(
            orderService.getOrder(BUYER_ID, Long.valueOf(orderId)), BUYER_ID, 500L));
        deliveredAtSetter.recordDelivered(Long.valueOf(orderId), SELLER_ID, LocalDateTime.now());
        String orderItemId = String.valueOf(orderService.getOrder(BUYER_ID, Long.valueOf(orderId))
            .items().getFirst().orderItemId());

        client.post().uri(ORDERS_PATH + "/" + orderId + "/items/" + orderItemId + "/confirm")
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CONFIRMED")
            .jsonPath("$.sellerName").isEqualTo("키들리샵");
    }

    private void assertSellerNames(String orderId, String expected) {
        client.get().uri(CART_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].sellerName").value(name -> assertThat(name).isEqualTo(expected));
        client.get().uri(ORDERS_PATH + "/" + orderId)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].sellerName").value(name -> assertThat(name).isEqualTo(expected));
        client.get().uri(ORDERS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].orderId").isEqualTo(orderId)
            .jsonPath("$.content[0].sellerName").value(name -> assertThat(name).isEqualTo(expected));
    }

    private void addToCart() {
        client.post().uri(CART_ITEMS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("optionId", "1", "quantity", 1))
            .exchange()
            .expectStatus().isCreated();
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

    private void publishSeller(LocalDateTime occurredAt, String businessName) {
        eventDelivery.deliver(new SellerChangedEvent(
            UUID.randomUUID(), occurredAt, new SellerChangedEvent.Data(SELLER_ID, businessName, SellerStatus.ACTIVE)));
    }
}
