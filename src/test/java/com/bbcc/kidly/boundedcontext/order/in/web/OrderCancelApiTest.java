package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.SellerOrderController.SELLER_ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.MarkPaidCommands;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.delivery.DeliveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 구매자 배송 전 전체 취소 API(#354). 결제완료는 다른 도메인 이벤트 대신 주문 서비스를 바로 불러 만든다.
 * 주문상품 2개: 옵션 1(판매자 2) · 옵션 2(판매자 3).
 */
@FunctionalTest
class OrderCancelApiTest {

    private static final Long OTHER_SELLER_ID = 3L;
    private static final Long OTHER_BUYER_ID = 9L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("결제대기 주문을 취소하면 200 · CANCELLED · BUYER_CANCELLED · 배송 [] · order.cancelled 1건")
    void cancelsPending() {
        Long orderId = place();

        cancel(orderId, BUYER_ID).expectStatus().isOk()
            .expectBody()
            .jsonPath("$.orderId").isEqualTo(String.valueOf(orderId))
            .jsonPath("$.status").isEqualTo("CANCELLED")
            .jsonPath("$.cancelReason").isEqualTo("BUYER_CANCELLED")
            .jsonPath("$.deliveries.length()").isEqualTo(0);

        assertThat(cancelledEvents(orderId)).singleElement()
            .satisfies(event -> assertThat(event.data().reason()).isEqualTo("BUYER_CANCELLED"));
    }

    @Test
    @DisplayName("결제완료 · 판매자 둘 다 발송 전이면 200, 배송 둘 다 CANCELLED(운송장 null), 다시 눌러도 200 · 이벤트 1건")
    void cancelsPaidBeforeShipping() {
        Long orderId = placeAndPay();

        cancel(orderId, BUYER_ID).expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")
            .jsonPath("$.cancelReason").isEqualTo("BUYER_CANCELLED")
            .jsonPath("$.deliveries.length()").isEqualTo(2)
            .jsonPath("$.deliveries[0].status").isEqualTo("CANCELLED")
            .jsonPath("$.deliveries[1].status").isEqualTo("CANCELLED")
            .jsonPath("$.deliveries[0].carrier").value(value -> assertThat(value).isNull())
            .jsonPath("$.deliveries[0].trackingNumber").value(value -> assertThat(value).isNull());
        cancel(orderId, BUYER_ID).expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED");

        assertThat(cancelledEvents(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("판매자 하나라도 발송했으면 409(판매자에게 문의 안내), 주문 · 배송 · 이벤트 모두 그대로")
    void rejectsWhenShipped() {
        Long orderId = placeAndPay();
        Delivery shipped = deliveryOf(orderId, SELLER_ID);
        deliveryService.ship(SELLER_ID, shipped.getId(), "CJ", "1234567890");

        cancel(orderId, BUYER_ID).expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("이미 발송된 상품이 있습니다")
            .jsonPath("$.detail").isEqualTo("판매자가 발송을 시작해 취소할 수 없습니다. 판매자에게 문의해 주세요.");

        assertThat(orderService.getOrder(BUYER_ID, orderId).status()).isEqualTo("PAID");
        assertThat(deliveryOf(orderId, OTHER_SELLER_ID).getStatus()).isEqualTo(Delivery.Status.READY);
        assertThat(cancelledEvents(orderId)).isEmpty();
    }

    @Test
    @DisplayName("다른 사유로 이미 취소된 주문은 200, 처음 사유 그대로 · 이벤트 추가 없음")
    void alreadyCancelledByOtherReason() {
        Long orderId = place();
        orderService.cancelForPaymentFailure(orderId);

        cancel(orderId, BUYER_ID).expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")
            .jsonPath("$.cancelReason").isEqualTo("PAYMENT_FAILED");

        assertThat(cancelledEvents(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("남의 주문과 없는 주문은 똑같이 404, 숫자가 아닌 ID는 400, 토큰이 없으면 401")
    void notFoundAndBadRequest() {
        Long orderId = place();

        cancel(orderId, OTHER_BUYER_ID).expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.title").isEqualTo("주문을 찾을 수 없습니다");
        cancel(orderId + 999, BUYER_ID).expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.title").isEqualTo("주문을 찾을 수 없습니다");
        client.post().uri(ORDERS_PATH + "/abc/cancel")
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isBadRequest();
        client.post().uri(ORDERS_PATH + "/" + orderId + "/cancel")
            .exchange()
            .expectStatus().isUnauthorized();
        assertThat(orderService.getOrder(BUYER_ID, orderId).status()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("취소된 배송은 판매자가 발송 · 배송 완료하면 409(발송하지 말라는 안내), 판매 주문 목록에는 CANCELLED로 남는다")
    void sellerSeesCancelledDelivery() {
        Long orderId = placeAndPay();
        cancel(orderId, BUYER_ID).expectStatus().isOk();
        Delivery delivery = deliveryOf(orderId, SELLER_ID);

        for (String action : List.of("ship", "deliver")) {
            client.post().uri(SELLER_ORDERS_PATH + "/" + delivery.getId() + "/" + action)
                .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
                .body(Map.of("carrier", "CJ", "trackingNumber", "1234567890"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.title").isEqualTo("구매자가 취소한 주문입니다")
                .jsonPath("$.detail").isEqualTo("구매자가 취소한 주문입니다. 발송하지 마세요.");
        }
        client.get().uri(SELLER_ORDERS_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(SELLER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].sellerOrderId").isEqualTo(String.valueOf(delivery.getId()))
            .jsonPath("$.content[0].status").isEqualTo("CANCELLED")
            .jsonPath("$.content[0].trackingNumber").value(value -> assertThat(value).isNull());
    }

    @Test
    @DisplayName("결제 뒤 취소한 주문의 타임라인은 PAID → CANCELLED, 배송 단계 details는 판매자별 NOT_APPLICABLE")
    void timelineAfterCancel() {
        Long orderId = placeAndPay();
        cancel(orderId, BUYER_ID).expectStatus().isOk();

        client.get().uri(ORDERS_PATH + "/" + orderId + "/timeline")
            .headers(headers -> headers.setBearerAuth(tokens.buyer(BUYER_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentStep").isEqualTo("CANCELLED")
            .jsonPath("$.cancelReason").isEqualTo("BUYER_CANCELLED")
            .jsonPath("$.steps[2].step").isEqualTo("PAID")
            .jsonPath("$.steps[3].step").isEqualTo("CANCELLED")
            .jsonPath("$.steps[4].step").isEqualTo("SHIPPED")
            .jsonPath("$.steps[4].status").isEqualTo("NOT_APPLICABLE")
            .jsonPath("$.steps[4].details.length()").isEqualTo(2)
            .jsonPath("$.steps[4].details[0].status").isEqualTo("NOT_APPLICABLE");
    }

    private RestTestClient.ResponseSpec cancel(Long orderId, Long memberId) {
        return client.post().uri(ORDERS_PATH + "/" + orderId + "/cancel")
            .headers(headers -> headers.setBearerAuth(tokens.buyer(memberId)))
            .exchange();
    }

    private Long place() {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L), new PlaceOrderCommand.Item(2L, null, 1, 30_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"))).orderId();
    }

    private Long placeAndPay() {
        Long orderId = place();
        OrderDetail order = orderService.getOrder(BUYER_ID, orderId);
        orderService.markPaid(MarkPaidCommands.matching(order, BUYER_ID, 500L));
        deliveryService.createFor(orderId);
        return orderId;
    }

    private Delivery deliveryOf(Long orderId, Long sellerId) {
        return deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId).stream()
            .filter(delivery -> delivery.getSellerId().equals(sellerId))
            .findFirst()
            .orElseThrow();
    }

    private List<OrderCancelledEvent> cancelledEvents(Long orderId) {
        return recorder.events(OrderCancelledEvent.class).stream()
            .filter(event -> event.data().orderId().equals(orderId))
            .toList();
    }
}
