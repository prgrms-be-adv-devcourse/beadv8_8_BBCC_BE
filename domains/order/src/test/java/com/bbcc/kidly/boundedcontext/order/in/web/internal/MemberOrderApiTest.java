package com.bbcc.kidly.boundedcontext.order.in.web.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 회원 최근 주문 내부 API(#1191, 챗봇): 최근 주문부터, 주문상품마다 그 판매자 배송, 배송지 · 연락처 · 송장번호 · 금액은 주지 않는다.
 */
@FunctionalTest
class MemberOrderApiTest {

    private static final String PATH = "/internal/members/{memberId}/orders";
    private static final Long MEMBER_ID = 9_100L;
    private static final Long SELLER_ID = 51L;
    private static final Long OTHER_SELLER_ID = 52L;
    private static final String RECIPIENT = "홍길동";
    private static final String PHONE = "010-1234-5678";
    private static final String ADDRESS = "서울시 테스트로 1";
    private static final String TRACKING_NUMBER = "987654321098";

    @Autowired
    private RestTestClient client;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Test
    @DisplayName("인증 헤더 없이 200: 최근 주문부터, 주문상품마다 그 판매자의 배송을 주고 개인정보 · 송장번호 · 금액 칸은 없다")
    void recentOrdersWithDeliveries() {
        LocalDateTime shippedAt = LocalDateTime.of(2026, 11, 10, 14, 30);
        Order older = place(MEMBER_ID, line(SELLER_ID, 1L, "여아 꽃무늬 원피스", "110 / 핑크", 2),
            line(OTHER_SELLER_ID, 2L, "남아 운동화", null, 1));
        pay(older);
        Delivery shipping = Delivery.create(older.getId(), SELLER_ID);
        shipping.ship(Delivery.Carrier.CJ, TRACKING_NUMBER, shippedAt);
        deliveryRepository.save(shipping);
        deliveryRepository.save(Delivery.create(older.getId(), OTHER_SELLER_ID));
        Order newer = place(MEMBER_ID, line(SELLER_ID, 3L, "래시가드", "120", 1));
        place(9_999L, line(SELLER_ID, 4L, "다른 회원 상품", null, 1));

        String raw = client.get().uri(PATH, MEMBER_ID)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody();
        List<Map<String, Object>> body = client.get().uri(PATH, MEMBER_ID)
            .exchange()
            .returnResult(new ParameterizedTypeReference<List<Map<String, Object>>>() { })
            .getResponseBody();

        assertThat(body).extracting(order -> order.get("orderId"))
            .containsExactly(newer.getId().intValue(), older.getId().intValue());
        assertThat(body.getFirst()).containsOnlyKeys("orderId", "orderedAt", "status", "items")
            .containsEntry("status", "PENDING_PAYMENT");
        assertThat(items(body.getFirst())).singleElement().satisfies(item -> assertThat(item)
            .containsEntry("productName", "래시가드")
            .containsEntry("optionName", "120")
            .containsEntry("quantity", 1)
            .containsEntry("status", "ORDERED")
            .containsEntry("delivery", null));

        Map<String, Object> paid = body.get(1);
        assertThat(paid).containsEntry("status", "PAID");
        List<Map<String, Object>> items = items(paid);
        assertThat(items.getFirst()).containsOnlyKeys("productName", "optionName", "quantity", "status", "delivery")
            .containsEntry("productName", "여아 꽃무늬 원피스")
            .containsEntry("optionName", "110 / 핑크")
            .containsEntry("quantity", 2)
            .containsEntry("status", "ORDERED");
        assertThat(delivery(items.getFirst())).containsOnlyKeys("status", "carrier", "shippedAt", "deliveredAt")
            .containsEntry("status", "SHIPPING")
            .containsEntry("carrier", "CJ")
            .containsEntry("shippedAt", "2026-11-10T14:30:00")
            .containsEntry("deliveredAt", null);
        assertThat(items.get(1)).containsEntry("productName", "남아 운동화").containsEntry("optionName", null);
        assertThat(delivery(items.get(1))).containsEntry("status", "READY").containsEntry("carrier", null);
        assertThat(raw).doesNotContain(RECIPIENT, PHONE, ADDRESS, TRACKING_NUMBER, "10000", "다른 회원 상품");
        assertThat(MemberOrderController.MEMBER_ORDERS_PATH).isEqualTo(PATH);
    }

    @Test
    @DisplayName("limit개까지만 주고, 주문이 없는 회원은 []")
    void limitAndEmpty() {
        place(MEMBER_ID, line(SELLER_ID, 1L, "상품 1", null, 1));
        Order newest = place(MEMBER_ID, line(SELLER_ID, 2L, "상품 2", null, 1));

        client.get().uri(PATH + "?limit=1", MEMBER_ID)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].orderId").isEqualTo(newest.getId());
        client.get().uri(PATH, 999_999)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("회원 ID가 숫자가 아니거나 1 미만, limit이 1~10 밖이면 400 ProblemDetail")
    void invalidInput() {
        for (String uri : List.of("/internal/members/abc/orders", "/internal/members/0/orders",
            "/internal/members/1/orders?limit=0", "/internal/members/1/orders?limit=11")) {
            client.get().uri(uri)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> order) {
        return (List<Map<String, Object>>) order.get("items");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> delivery(Map<String, Object> item) {
        return (Map<String, Object>) item.get("delivery");
    }

    private Order place(Long memberId, Order.RequestedItem... lines) {
        return orderRepository.save(Order.place(memberId, List.of(lines),
            new Order.ShippingAddress(RECIPIENT, PHONE, "12345", ADDRESS),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now()));
    }

    private void pay(Order order) {
        order.markPaid(1L, order.getMemberId(), order.getTotalAmount(), order.getItems().stream()
            .map(item -> new Order.PaidItem(item.getId(), item.getAmount()))
            .toList(), LocalDateTime.now());
        orderRepository.save(order);
    }

    private static Order.RequestedItem line(Long sellerId, Long optionId, String productName, String optionName,
        int quantity) {
        return new Order.RequestedItem(new OrderItem.ProductSnapshot(optionId, optionId, sellerId, productName,
            optionName, null, 10_000L, 1L), true, null, quantity, 10_000L);
    }
}
