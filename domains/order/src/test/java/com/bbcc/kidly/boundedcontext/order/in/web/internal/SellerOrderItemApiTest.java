package com.bbcc.kidly.boundedcontext.order.in.web.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 판매자 미종결 주문상품 수 내부 API(#1120): 경로 · 응답 칸 이름 두 개 고정, 인증 헤더 없이 200, 잘못된 판매자 ID는 400 ProblemDetail.
 */
@FunctionalTest
class SellerOrderItemApiTest {

    private static final String PATH = "/internal/sellers/{sellerId}/order-items/summary";
    private static final Long SELLER_ID = 41L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("인증 헤더 없이 200: 응답 칸은 sellerId(숫자) · openItemCount 두 개뿐이다")
    void summaryWithoutAuthHeaders() {
        orderRepository.save(Order.place(8_200L, List.of(line(SELLER_ID, 1L), line(SELLER_ID, 2L), line(42L, 3L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now()));

        Map<String, Object> body = client.get().uri(PATH, SELLER_ID)
            .exchange()
            .expectStatus().isOk()
            .returnResult(new ParameterizedTypeReference<Map<String, Object>>() { })
            .getResponseBody();

        assertThat(body).containsOnlyKeys("sellerId", "openItemCount")
            .containsEntry("sellerId", 41)
            .containsEntry("openItemCount", 2);
        assertThat(SellerOrderItemController.SELLER_ORDER_ITEMS_SUMMARY_PATH).isEqualTo(PATH);
    }

    @Test
    @DisplayName("주문이 없는 판매자는 openItemCount 0")
    void zeroForSellerWithoutOrders() {
        client.get().uri(PATH, 999_999)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.sellerId").isEqualTo(999_999)
            .jsonPath("$.openItemCount").isEqualTo(0);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "99999999999999999999"})
    @DisplayName("판매자 ID가 숫자가 아니거나 1 미만이면 400 ProblemDetail(받는 쪽이 고칠 수 있는 메시지)")
    void invalidSellerId(String sellerId) {
        client.get().uri("/internal/sellers/" + sellerId + "/order-items/summary")
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.title").isEqualTo("잘못된 판매자 ID입니다")
            .jsonPath("$.detail").isEqualTo("판매자 ID는 1 이상의 숫자여야 합니다.");
    }

    private static Order.RequestedItem line(Long sellerId, Long optionId) {
        return new Order.RequestedItem(new OrderItem.ProductSnapshot(optionId, optionId, sellerId, "상품", null, null,
            10_000L, 1L), true, null, 1, 10_000L);
    }
}
