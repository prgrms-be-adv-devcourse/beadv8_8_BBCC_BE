package com.bbcc.kidly.boundedcontext.order.in.web.admin;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.admin.AdminOrderController.ADMIN_ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.scheduler.OrderScheduler;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.global.security.TestMembers;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자 주문 목록의 취소 시각 · 취소일 기준 조회(#1325). 취소 시각은 상태 이력의 주문 → CANCELLED 시각이다.
 * 데이터: 어제 생성 · 오늘 취소(A), 오늘 생성 · 어제 취소(B, 이력 시각을 어제로), 취소 아님(C), 결제 마감 만료 자동 취소(D).
 */
@FunctionalTest
class AdminOrderCancelledApiTest {

    private static final Long ADMIN_ID = 99L;
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");
    private static final String TODAY = LocalDate.now().atStartOfDay()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    private static final LocalDateTime YESTERDAY_NOON = LocalDate.now().minusDays(1).atTime(12, 0);

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderScheduler orderScheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String createdYesterdayCancelledToday;
    private String cancelledYesterday;
    private String notCancelled;
    private String expired;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        createdYesterdayCancelledToday = placeOrder();
        jdbcTemplate.update("update orders.orders set created_at = ? where id = ?", YESTERDAY_NOON,
            Long.valueOf(createdYesterdayCancelledToday));
        orderService.cancelForPaymentFailure(Long.valueOf(createdYesterdayCancelledToday));

        cancelledYesterday = placeOrder();
        orderService.cancelForPaymentFailure(Long.valueOf(cancelledYesterday));
        jdbcTemplate.update("update orders.order_status_history set occurred_at = ? where order_id = ? "
            + "and target_type = 'ORDER' and to_status = 'CANCELLED'", YESTERDAY_NOON,
            Long.valueOf(cancelledYesterday));

        notCancelled = placeOrder();

        expired = placeOrder();
        jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(1), Long.valueOf(expired));
        orderScheduler.expireOverduePayments();
    }

    @Test
    @DisplayName("목록 줄마다 cancelledAt: 취소 주문은 상태 이력의 취소 시각, 취소 아닌 주문은 null")
    void listHasCancelledAt() {
        list("")
            .jsonPath("$.content[?(@.orderId == '" + notCancelled + "')].cancelledAt")
            .value(values -> assertThat((List<?>) values).hasSize(1).allMatch(Objects::isNull))
            .jsonPath("$.content[?(@.orderId == '" + cancelledYesterday + "')].cancelledAt")
            .value(values -> assertThat(String.valueOf(((List<?>) values).getFirst()))
                .startsWith(YESTERDAY_NOON.toLocalDate() + "T12:00"))
            .jsonPath("$.content[?(@.orderId == '" + expired + "')].status").isEqualTo(List.of("CANCELLED"))
            .jsonPath("$.content[?(@.orderId == '" + expired + "')].cancelledAt")
            .value(values -> assertThat(((List<?>) values).getFirst()).isNotNull())
            .jsonPath("$.content[?(@.orderId == '" + createdYesterdayCancelledToday + "')].cancelledAt")
            .value(values -> assertThat(String.valueOf(((List<?>) values).getFirst()))
                .startsWith(LocalDate.now().toString()));
    }

    @Test
    @DisplayName("cancelledFrom=오늘 0시: 어제 생성 · 오늘 취소와 만료 자동 취소는 포함, 오늘 생성 · 어제 취소와 취소 아닌 주문은 제외(최근 주문부터)")
    void cancelledFromToday() {
        list("?cancelledFrom=" + TODAY)
            .jsonPath("$.content[*].orderId").isEqualTo(List.of(expired, createdYesterdayCancelledToday))
            .jsonPath("$.content[*].cancelledAt").value(values -> assertThat((List<?>) values).doesNotContainNull())
            .jsonPath("$.totalElements").isEqualTo(2);
    }

    @Test
    @DisplayName("status=CANCELLED&cancelledFrom=오늘 0시&size=1의 totalElements가 오늘 취소 건수(2), status가 다르면 0건")
    void todayCancelledCount() {
        list("?status=CANCELLED&cancelledFrom=" + TODAY + "&size=1")
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.totalPages").isEqualTo(2);
        list("?status=PAID&cancelledFrom=" + TODAY).jsonPath("$.totalElements").isEqualTo(0);
        list("?status=PENDING_PAYMENT&cancelledFrom=" + TODAY).jsonPath("$.totalElements").isEqualTo(0);
        list("?cancelledFrom=" + TODAY + "&page=1073741824&size=2")
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(2);
    }

    @Test
    @DisplayName("cancelledFrom은 그 시각을 포함한다(>=): 어제 취소 시각 그대로 주면 어제 취소도 나온다")
    void cancelledFromIsInclusive() {
        list("?cancelledFrom=" + YESTERDAY_NOON.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
            .jsonPath("$.totalElements").isEqualTo(3);
    }

    @ParameterizedTest(name = "cancelledFrom=\"{0}\"")
    @ValueSource(strings = {"", "2026-11-12", "2026-11-12T00:00", "2026-11-12T00:00:00+09:00", "2026-11-12 00:00:00",
        "2026-11-12T00:00:00.000", "2026-02-30T00:00:00", "today"})
    @DisplayName("형식이 틀리면(빈 값 · 날짜만 · 초 없음 · 오프셋 · 공백 · 밀리초 · 없는 날짜) 400 INVALID_CANCELLED_FROM(형식 안내)")
    void invalidCancelledFrom(String value) {
        client.get().uri(uri -> uri.path(ADMIN_ORDERS_PATH).queryParam("cancelledFrom", value).build())
            .headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("취소 시각 형식이 맞지 않습니다")
            .jsonPath("$.detail").value(detail -> assertThat((String) detail).contains("yyyy-MM-ddTHH:mm:ss"));
    }

    private RestTestClient.BodyContentSpec list(String query) {
        return client.get().uri(ADMIN_ORDERS_PATH + query)
            .headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody();
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
