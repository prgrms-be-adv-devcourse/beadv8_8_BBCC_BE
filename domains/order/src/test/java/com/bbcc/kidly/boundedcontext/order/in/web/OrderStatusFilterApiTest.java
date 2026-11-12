package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.admin.AdminOrderController.ADMIN_ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderQueryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 구매자 내 주문 · 관리자 전체 주문 목록의 주문 상태 필터(#1280): 응답 status와 같은 값 하나로 DB에서 거르고, totalElements가 페이지 밖까지
 * 그 상태의 전체 건수다. 필터가 없으면 지금과 같고, 모르는 값 · 소문자 · 빈 값은 400 INVALID_ORDER_STATUS.
 * 데이터: 구매자 주문 결제대기 3 · 결제완료 1 · 취소 1, 다른 회원 결제대기 1.
 */
@FunctionalTest
class OrderStatusFilterApiTest {

    private static final Long OTHER_BUYER_ID = 3L;
    private static final Long ADMIN_ID = 99L;
    private static final String INVALID_TITLE = "지원하지 않는 주문 상태입니다";
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderQueryService orderQueries;

    @Autowired
    private OrderEventDelivery eventDelivery;

    private String pending1;
    private String pending2;
    private String pending3;
    private String paid;
    private String cancelled;
    private String otherPending;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        pending1 = placeOrder(BUYER_ID);
        paid = placeOrder(BUYER_ID);
        pay(paid);
        pending2 = placeOrder(BUYER_ID);
        cancelled = placeOrder(BUYER_ID);
        orderService.cancelForPaymentFailure(Long.valueOf(cancelled));
        otherPending = placeOrder(OTHER_BUYER_ID);
        pending3 = placeOrder(BUYER_ID);
    }

    @Test
    @DisplayName("구매자: 상태마다 내 주문 중 그 상태만 최근 주문부터, totalElements는 그 상태 건수(남의 주문 없음)")
    void buyerFiltersEachStatus() {
        list(ORDERS_PATH + "?status=PENDING_PAYMENT", TestMembers.buyer(BUYER_ID))
            .jsonPath("$.content[*].orderId").isEqualTo(List.of(pending3, pending2, pending1))
            .jsonPath("$.content[*].status").isEqualTo(List.of("PENDING_PAYMENT", "PENDING_PAYMENT", "PENDING_PAYMENT"))
            .jsonPath("$.totalElements").isEqualTo(3);
        list(ORDERS_PATH + "?status=PAID", TestMembers.buyer(BUYER_ID))
            .jsonPath("$.content[*].orderId").isEqualTo(List.of(paid))
            .jsonPath("$.content[0].status").isEqualTo("PAID")
            .jsonPath("$.content[0].representativeProductName").exists()
            .jsonPath("$.totalElements").isEqualTo(1);
        list(ORDERS_PATH + "?status=CANCELLED", TestMembers.buyer(BUYER_ID))
            .jsonPath("$.content[*].orderId").isEqualTo(List.of(cancelled))
            .jsonPath("$.content[0].status").isEqualTo("CANCELLED")
            .jsonPath("$.totalElements").isEqualTo(1);
        list(ORDERS_PATH + "?status=PAID", TestMembers.buyer(OTHER_BUYER_ID))
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    @DisplayName("구매자: status가 없으면 지금과 같다(내 주문 전체 · 최근 주문부터 · totalElements 5)")
    void buyerWithoutStatusIsUnchanged() {
        list(ORDERS_PATH, TestMembers.buyer(BUYER_ID))
            .jsonPath("$.content[*].orderId").isEqualTo(List.of(pending3, cancelled, pending2, paid, pending1))
            .jsonPath("$.content[*].status")
            .isEqualTo(List.of("PENDING_PAYMENT", "CANCELLED", "PENDING_PAYMENT", "PAID", "PENDING_PAYMENT"))
            .jsonPath("$.totalElements").isEqualTo(5);
    }

    @Test
    @DisplayName("구매자: 페이지 크기(2)보다 그 상태가 많으면(3) totalElements 3 · totalPages 2이고 다음 페이지에 나머지 1건")
    void buyerTotalBeyondPage() {
        list(ORDERS_PATH + "?status=PENDING_PAYMENT&size=2", TestMembers.buyer(BUYER_ID))
            .jsonPath("$.content[*].orderId").isEqualTo(List.of(pending3, pending2))
            .jsonPath("$.totalElements").isEqualTo(3)
            .jsonPath("$.totalPages").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(true);
        list(ORDERS_PATH + "?status=PENDING_PAYMENT&size=2&page=1", TestMembers.buyer(BUYER_ID))
            .jsonPath("$.content[*].orderId").isEqualTo(List.of(pending1))
            .jsonPath("$.totalElements").isEqualTo(3)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("구매자 · 관리자: offset이 int를 넘는 큰 page는 빈 목록이고 totalElements는 그 상태 건수")
    void hugePageCountsStatus() {
        list(ORDERS_PATH + "?status=PENDING_PAYMENT&page=1073741824&size=2", TestMembers.buyer(BUYER_ID))
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(3);
        list(ADMIN_ORDERS_PATH + "?status=PENDING_PAYMENT&page=1073741824&size=2", TestAdmins.of(ADMIN_ID))
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(4);
    }

    @Test
    @DisplayName("관리자: 상태마다 모든 회원의 그 상태만 최근 주문부터, 페이지 밖까지 totalElements가 맞다")
    void adminFiltersEachStatus() {
        list(ADMIN_ORDERS_PATH + "?status=PENDING_PAYMENT&size=2", TestAdmins.of(ADMIN_ID))
            .jsonPath("$.content[*].orderId").isEqualTo(List.of(pending3, otherPending))
            .jsonPath("$.totalElements").isEqualTo(4)
            .jsonPath("$.totalPages").isEqualTo(2);
        list(ADMIN_ORDERS_PATH + "?status=PENDING_PAYMENT&size=2&page=1", TestAdmins.of(ADMIN_ID))
            .jsonPath("$.content[*].orderId").isEqualTo(List.of(pending2, pending1));
        list(ADMIN_ORDERS_PATH + "?status=PAID", TestAdmins.of(ADMIN_ID))
            .jsonPath("$.content[*].orderId").isEqualTo(List.of(paid))
            .jsonPath("$.content[0].itemCount").isEqualTo(1)
            .jsonPath("$.totalElements").isEqualTo(1);
        list(ADMIN_ORDERS_PATH + "?status=CANCELLED", TestAdmins.of(ADMIN_ID))
            .jsonPath("$.content[*].orderId").isEqualTo(List.of(cancelled))
            .jsonPath("$.content[0].cancelReason").isEqualTo("PAYMENT_FAILED")
            .jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    @DisplayName("관리자: status가 없으면 지금과 같다(전체 6건 · 최근 주문부터)")
    void adminWithoutStatusIsUnchanged() {
        list(ADMIN_ORDERS_PATH, TestAdmins.of(ADMIN_ID))
            .jsonPath("$.content[*].orderId")
            .isEqualTo(List.of(pending3, otherPending, cancelled, pending2, paid, pending1))
            .jsonPath("$.totalElements").isEqualTo(6);
    }

    @ParameterizedTest(name = "status=\"{0}\"")
    @ValueSource(strings = {"paid", "Paid", "", " ", "SHIPPING", "PAID,CANCELLED", "UNKNOWN"})
    @DisplayName("모르는 값 · 소문자 · 빈 값 · 여러 값은 구매자 · 관리자 모두 400 INVALID_ORDER_STATUS(받는 값 안내)")
    void invalidStatusIsBadRequest(String status) {
        for (Map.Entry<String, Consumer<HttpHeaders>> caller : Map.of(
            ORDERS_PATH, TestMembers.buyer(BUYER_ID), ADMIN_ORDERS_PATH, TestAdmins.of(ADMIN_ID)).entrySet()) {
            client.get().uri(uri -> uri.path(caller.getKey()).queryParam("status", status).build())
                .headers(caller.getValue())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo(INVALID_TITLE)
                .jsonPath("$.detail").value(detail -> assertThat((String) detail).contains("PENDING_PAYMENT"));
        }
    }

    private RestTestClient.BodyContentSpec list(String uri, Consumer<HttpHeaders> headers) {
        return client.get().uri(uri)
            .headers(headers)
            .exchange()
            .expectStatus().isOk()
            .expectBody();
    }

    private String placeOrder(Long memberId) {
        URI location = client.post().uri(ORDERS_PATH)
            .headers(TestMembers.buyer(memberId))
            .body(Map.of("items", List.of(Map.of("optionId", "1", "quantity", 1, "unitPrice", 25_000)),
                "shippingAddress", ADDRESS))
            .exchange()
            .expectStatus().isCreated()
            .expectBody().returnResult()
            .getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((ORDERS_PATH + "/").length());
    }

    // 주문 모듈 테스트에는 결제 도메인이 없으므로 재고 차감 · 결제 완료됨을 직접 넣는다(#616)
    private void pay(String orderId) {
        Long id = Long.valueOf(orderId);
        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(id)));
        eventDelivery.deliver(PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, id), BUYER_ID));
    }
}
