package com.bbcc.kidly.boundedcontext.delivery.in.web;

import static com.bbcc.kidly.boundedcontext.delivery.in.web.SellerOrderController.SELLER_ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderQueryService;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 판매자 판매 주문(= 배송) 목록의 배송 상태 필터(#1280): 응답 status와 같은 값 하나로 그 판매자의 배송만 DB에서 거르고, totalElements가
 * 페이지 밖까지 그 상태의 전체 건수다. 필터가 없으면 지금과 같고, 모르는 값 · 소문자 · 빈 값은 400 INVALID_DELIVERY_STATUS.
 * 데이터: 결제된 주문 6건(판매자 A 상품 + 판매자 B 상품) → 판매자 A 배송 READY 3 · SHIPPING 1 · DELIVERED 1 · CANCELLED 1,
 * 판매자 B 배송 READY 6.
 */
@FunctionalTest
class SellerOrderStatusFilterApiTest {

    private static final Long OTHER_SELLER_ID = 3L;
    private static final String INVALID_TITLE = "지원하지 않는 판매 주문 상태입니다";
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderQueryService orderQueries;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 판매자 A 배송 ID, 만든 순서(주문 순서)
    private final List<String> deliveries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
        deliveries.clear();
        for (int i = 0; i < 6; i++) {
            Long orderId = placePaidOrder();
            deliveries.add(String.valueOf(jdbcTemplate.queryForObject(
                "select id from orders.delivery where order_id = ? and seller_id = ?", Long.class, orderId,
                SELLER_ID)));
        }
        // 0 · 1 · 2 READY, 3 SHIPPING, 4 DELIVERED, 5 CANCELLED
        ship(deliveries.get(3));
        ship(deliveries.get(4));
        client.post().uri(SELLER_ORDERS_PATH + "/" + deliveries.get(4) + "/deliver")
            .headers(TestMembers.seller(SELLER_ID))
            .exchange()
            .expectStatus().isOk();
        client.post().uri(SELLER_ORDERS_PATH + "/" + deliveries.get(5) + "/cancel")
            .headers(TestMembers.seller(SELLER_ID))
            .body(Map.of("reason", "OUT_OF_STOCK"))
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("상태마다 그 판매자의 그 상태 배송만 최근 것부터, totalElements는 그 상태 건수")
    void filtersEachStatus() {
        list(SELLER_ID, "?status=READY")
            .jsonPath("$.content[*].sellerOrderId")
            .isEqualTo(List.of(deliveries.get(2), deliveries.get(1), deliveries.get(0)))
            .jsonPath("$.content[*].status").isEqualTo(List.of("READY", "READY", "READY"))
            .jsonPath("$.totalElements").isEqualTo(3);
        list(SELLER_ID, "?status=SHIPPING")
            .jsonPath("$.content[*].sellerOrderId").isEqualTo(List.of(deliveries.get(3)))
            .jsonPath("$.content[0].carrier").isEqualTo("CJ")
            .jsonPath("$.content[0].items.length()").isEqualTo(1)
            .jsonPath("$.totalElements").isEqualTo(1);
        list(SELLER_ID, "?status=DELIVERED")
            .jsonPath("$.content[*].sellerOrderId").isEqualTo(List.of(deliveries.get(4)))
            .jsonPath("$.totalElements").isEqualTo(1);
        list(SELLER_ID, "?status=CANCELLED")
            .jsonPath("$.content[*].sellerOrderId").isEqualTo(List.of(deliveries.get(5)))
            .jsonPath("$.content[0].status").isEqualTo("CANCELLED")
            .jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    @DisplayName("다른 판매자 배송은 섞이지 않는다: 판매자 B는 READY 6 · 나머지 상태 0")
    void onlyOwnDeliveries() {
        list(OTHER_SELLER_ID, "?status=READY&size=100")
            .jsonPath("$.content[*].sellerOrderId")
            .value(ids -> assertThat(strings(ids)).hasSize(6).doesNotContainAnyElementsOf(deliveries))
            .jsonPath("$.content[*].items[*].sellerId")
            .value(ids -> assertThat(strings(ids)).containsOnly(String.valueOf(OTHER_SELLER_ID)))
            .jsonPath("$.totalElements").isEqualTo(6);
        list(OTHER_SELLER_ID, "?status=CANCELLED").jsonPath("$.totalElements").isEqualTo(0);
        list(OTHER_SELLER_ID, "?status=SHIPPING").jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    @DisplayName("페이지 크기(2)보다 그 상태가 많으면(3) totalElements 3 · totalPages 2이고 다음 페이지에 나머지 1건")
    void totalBeyondPage() {
        list(SELLER_ID, "?status=READY&size=2")
            .jsonPath("$.content[*].sellerOrderId").isEqualTo(List.of(deliveries.get(2), deliveries.get(1)))
            .jsonPath("$.totalElements").isEqualTo(3)
            .jsonPath("$.totalPages").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(true);
        list(SELLER_ID, "?status=READY&size=2&page=1")
            .jsonPath("$.content[*].sellerOrderId").isEqualTo(List.of(deliveries.get(0)))
            .jsonPath("$.hasNext").isEqualTo(false);
        list(SELLER_ID, "?status=READY&page=1073741824&size=2")
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(3);
    }

    @Test
    @DisplayName("status가 없으면 지금과 같다(그 판매자 배송 6건 · 최근 것부터)")
    void withoutStatusIsUnchanged() {
        list(SELLER_ID, "")
            .jsonPath("$.content[*].sellerOrderId").isEqualTo(List.copyOf(deliveries.reversed()))
            .jsonPath("$.content[*].status")
            .isEqualTo(List.of("CANCELLED", "DELIVERED", "SHIPPING", "READY", "READY", "READY"))
            .jsonPath("$.totalElements").isEqualTo(6);
    }

    @ParameterizedTest(name = "status=\"{0}\"")
    @ValueSource(strings = {"ready", "Ready", "", " ", "PAID", "PENDING_PAYMENT", "READY,SHIPPING"})
    @DisplayName("모르는 값 · 소문자 · 빈 값 · 주문 상태 · 여러 값은 400 INVALID_DELIVERY_STATUS(받는 값 안내)")
    void invalidStatusIsBadRequest(String status) {
        client.get().uri(uri -> uri.path(SELLER_ORDERS_PATH).queryParam("status", status).build())
            .headers(TestMembers.seller(SELLER_ID))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo(INVALID_TITLE)
            .jsonPath("$.detail").value(detail -> assertThat((String) detail).contains("READY · SHIPPING"));
    }

    private static List<String> strings(Object values) {
        return ((List<?>) values).stream().map(String::valueOf).toList();
    }

    private RestTestClient.BodyContentSpec list(Long sellerId, String query) {
        return client.get().uri(SELLER_ORDERS_PATH + query)
            .headers(TestMembers.seller(sellerId))
            .exchange()
            .expectStatus().isOk()
            .expectBody();
    }

    private void ship(String deliveryId) {
        client.post().uri(SELLER_ORDERS_PATH + "/" + deliveryId + "/ship")
            .headers(TestMembers.seller(SELLER_ID))
            .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
            .exchange()
            .expectStatus().isOk();
    }

    private Long placePaidOrder() {
        URI location = client.post().uri(ORDERS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of(
                "items", List.of(
                    Map.of("optionId", "1", "quantity", 1, "unitPrice", 25_000),
                    Map.of("optionId", "2", "quantity", 1, "unitPrice", 30_000)),
                "shippingAddress", ADDRESS))
            .exchange()
            .expectStatus().isCreated()
            .expectBody().returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        Long id = Long.valueOf(location.getPath().substring((ORDERS_PATH + "/").length()));
        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(id)));
        // 주문 모듈 테스트에는 결제 도메인이 없으므로 결제 완료됨을 직접 넣는다(#616)
        eventDelivery.deliver(PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, id), BUYER_ID));
        return id;
    }
}
