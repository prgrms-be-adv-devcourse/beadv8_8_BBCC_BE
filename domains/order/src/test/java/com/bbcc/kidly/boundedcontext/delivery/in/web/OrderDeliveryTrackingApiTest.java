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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 구매자 택배 진행 이력 API(#1320) 거절 경로. 테스트 설정은 택배 조회가 꺼져 있어(인증값 없음) 조회 가능한 배송은 503이다.
 * 정상 응답 · 변환 · 캐시는 단위 테스트(TrackingHistoryServiceTest · TrackingHistoryCheckerTest)가 가짜 외부 응답으로 본다.
 */
@FunctionalTest
class OrderDeliveryTrackingApiTest {

    private static final Long OTHER_BUYER_ID = 3L;
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

    private Long orderId;
    private Long deliveryId;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        orderId = placePaidOrder();
        deliveryId = jdbcTemplate.queryForObject("select id from orders.delivery where order_id = ?", Long.class,
            orderId);
    }

    @Test
    @DisplayName("발송 전(운송장 없음)이면 200 · events [] · carrier · trackingNumber null(sellerOrderId = 배송 ID)")
    void notShippedIsEmpty() {
        tracking(BUYER_ID, orderId, deliveryId)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.deliveryId").isEqualTo(String.valueOf(deliveryId))
            .jsonPath("$.carrier").value(carrier -> assertThat(carrier).isNull())
            .jsonPath("$.trackingNumber").value(number -> assertThat(number).isNull())
            .jsonPath("$.events.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("발송됐지만 택배 조회가 꺼져 있으면 503 TRACKING_UNAVAILABLE(다시 시도 안내)")
    void disabledIsServiceUnavailable() {
        ship("CJ", "1234567890");

        tracking(BUYER_ID, orderId, deliveryId)
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.title").isEqualTo("택배 조회를 할 수 없습니다")
            .jsonPath("$.detail").isEqualTo("지금 택배 조회를 할 수 없어요. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    @DisplayName("테스트 택배사(DUMMY)는 조회가 꺼져 있어도 200 · events [] (택배사 · 운송장은 그대로)")
    void dummyIsEmpty() {
        ship("DUMMY", "2026-11-12T09:00:00Z");

        tracking(BUYER_ID, orderId, deliveryId)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.carrier").isEqualTo("DUMMY")
            .jsonPath("$.trackingNumber").isEqualTo("2026-11-12T09:00:00Z")
            .jsonPath("$.events.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("남의 주문 · 다른 주문 ID · 없는 배송은 모두 404 TRACKING_DELIVERY_NOT_FOUND(있는지 알려 주지 않는다)")
    void notFoundCases() {
        ship("CJ", "1234567890");

        for (RestTestClient.ResponseSpec response : List.of(
            tracking(OTHER_BUYER_ID, orderId, deliveryId),
            tracking(BUYER_ID, orderId + 1, deliveryId),
            tracking(BUYER_ID, orderId, deliveryId + 1000))) {
            response.expectStatus().isNotFound()
                .expectBody().jsonPath("$.title").isEqualTo("배송을 찾을 수 없습니다");
        }
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void unauthorized() {
        client.get().uri(OrderDeliveryTrackingController.TRACKING_PATH, orderId, deliveryId)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec tracking(Long memberId, Long order, Long delivery) {
        return client.get().uri(OrderDeliveryTrackingController.TRACKING_PATH, order, delivery)
            .headers(TestMembers.buyer(memberId))
            .exchange();
    }

    private void ship(String carrier, String trackingNumber) {
        client.post().uri(SELLER_ORDERS_PATH + "/" + deliveryId + "/ship")
            .headers(TestMembers.seller(SELLER_ID))
            .body(Map.of("carrier", carrier, "trackingNumber", trackingNumber))
            .exchange()
            .expectStatus().isOk();
    }

    private Long placePaidOrder() {
        URI location = client.post().uri(ORDERS_PATH)
            .headers(TestMembers.buyer(BUYER_ID))
            .body(Map.of("items", List.of(Map.of("optionId", "1", "quantity", 1, "unitPrice", 25_000)),
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
