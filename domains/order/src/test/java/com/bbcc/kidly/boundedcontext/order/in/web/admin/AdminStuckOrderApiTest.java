package com.bbcc.kidly.boundedcontext.order.in.web.admin;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.admin.AdminOrderController.ADMIN_ORDERS_PATH;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.out.outbox.OrderOutboxMessage;
import com.bbcc.kidly.boundedcontext.order.out.persistence.JpaOrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자 멈춘 주문 조회 API(#445): 건수 요약 · 종류별 목록. 경계 · 종류별 조건은 StuckOrderIntegrationTest가 본다.
 */
@FunctionalTest
class AdminStuckOrderApiTest {

    private static final Long ADMIN_ID = 99L;
    private static final String STUCK_PATH = ADMIN_ORDERS_PATH + "/stuck";

    @Autowired
    private RestTestClient client;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JpaOrderOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("건수 요약: 일곱 종류 키를 모두(0 포함) 담은 객체")
    void summary() {
        stockCheckStale(LocalDateTime.now().minusHours(2));

        client.get().uri(STUCK_PATH + "/summary")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .json("""
                {"STOCK_CHECK_STALE":1,"PAYMENT_EXPIRY_MISSED":0,"PAID_WITHOUT_DELIVERY":0,
                 "RECOVERY_PENDING_STALE":0,"SHIPPING_DELAYED":0,"AUTO_CONFIRM_MISSED":0,"OUTBOX_STUCK":0}""",
                JsonCompareMode.STRICT);
    }

    @Test
    @DisplayName("목록: 오래된 것부터, 관리자 목록과 같은 페이지 모양, ID는 문자열")
    void listsOldestFirst() {
        LocalDateTime base = LocalDateTime.now().withNano(0).minusHours(2);
        String newer = stockCheckStale(base);
        String older = stockCheckStale(base.minusMinutes(1));

        client.get().uri(STUCK_PATH + "?kind=STOCK_CHECK_STALE&size=1")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].kind").isEqualTo("STOCK_CHECK_STALE")
            .jsonPath("$.content[0].orderId").isEqualTo(older)
            .jsonPath("$.content[0].targetType").isEqualTo("ORDER")
            .jsonPath("$.content[0].targetId").isEqualTo(older)
            .jsonPath("$.content[0].status").isEqualTo("PENDING_PAYMENT")
            .jsonPath("$.content[0].since")
            .isEqualTo(base.minusMinutes(1).plusMinutes(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .jsonPath("$.content[0].outbox").isEmpty()
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(1)
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.totalPages").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(true);
        client.get().uri(STUCK_PATH + "?kind=STOCK_CHECK_STALE&page=1&size=1")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].orderId").isEqualTo(newer)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("아웃박스 멈춤: 맨 앞 미전송 메시지 한 건과 outbox 칸(ID는 문자열), 요약에 1")
    void outboxStuck() {
        LocalDateTime createdAt = LocalDateTime.now().withNano(0).minusHours(1);
        UUID eventId = UUID.randomUUID();
        Long outboxId = outboxRepository.save(OrderOutboxMessage.create(eventId, "order.cancelled",
            "kidly.order.events", "1024", "{}")).getId();
        outboxRepository.save(OrderOutboxMessage.create(UUID.randomUUID(), "order.created", "kidly.order.events",
            "1025", "{}"));
        jdbcTemplate.update("update orders.order_outbox set created_at = ?, attempt_count = 12, "
            + "last_error = 'RecordTooLargeException: 크기 초과' where id = ?", createdAt, outboxId);

        client.get().uri(STUCK_PATH + "?kind=OUTBOX_STUCK")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].kind").isEqualTo("OUTBOX_STUCK")
            .jsonPath("$.content[0].orderId").isEqualTo("1024")
            .jsonPath("$.content[0].targetType").isEqualTo("ORDER_OUTBOX")
            .jsonPath("$.content[0].targetId").isEqualTo(String.valueOf(outboxId))
            .jsonPath("$.content[0].status").isEqualTo("UNSENT")
            .jsonPath("$.content[0].since")
            .isEqualTo(createdAt.plusMinutes(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .jsonPath("$.content[0].outbox.outboxId").isEqualTo(String.valueOf(outboxId))
            .jsonPath("$.content[0].outbox.eventType").isEqualTo("order.cancelled")
            .jsonPath("$.content[0].outbox.eventId").isEqualTo(eventId.toString())
            .jsonPath("$.content[0].outbox.attemptCount").isEqualTo(12)
            .jsonPath("$.content[0].outbox.createdAt")
            .isEqualTo(createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .jsonPath("$.content[0].outbox.lastError").isEqualTo("RecordTooLargeException: 크기 초과")
            .jsonPath("$.content[0].outbox.backlogCount").isEqualTo(1)
            .jsonPath("$.totalElements").isEqualTo(1);
        client.get().uri(STUCK_PATH + "/summary")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.OUTBOX_STUCK").isEqualTo(1);
    }

    @Test
    @DisplayName("멈춘 대상이 없으면 200 · content []")
    void emptyList() {
        client.get().uri(STUCK_PATH + "?kind=SHIPPING_DELAYED")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    @ParameterizedTest(name = "query={0}")
    @ValueSource(strings = {"", "?kind=", "?kind=UNKNOWN", "?kind=stock_check_stale"})
    @DisplayName("kind 없음 · 빈 값 · 모르는 값 · 소문자면 400 INVALID_STUCK_KIND, 받는 값 일곱 개를 안내한다")
    void unknownKindIsBadRequest(String query) {
        client.get().uri(STUCK_PATH + query)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("지원하지 않는 멈춘 주문 종류입니다")
            .jsonPath("$.detail").isEqualTo("kind는 STOCK_CHECK_STALE · PAYMENT_EXPIRY_MISSED · PAID_WITHOUT_DELIVERY · "
                + "RECOVERY_PENDING_STALE · SHIPPING_DELAYED · AUTO_CONFIRM_MISSED · OUTBOX_STUCK 중 하나를 대문자 그대로 "
                + "보내 주세요.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"?kind=SHIPPING_DELAYED&size=0", "?kind=SHIPPING_DELAYED&size=101",
        "?kind=SHIPPING_DELAYED&page=-1"})
    @DisplayName("size 1~100 밖 · page 음수면 400")
    void invalidParametersAreBadRequest(String query) {
        client.get().uri(STUCK_PATH + query)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("offset이 int를 넘는 큰 page(1073741824 · size 2)는 200 · 빈 목록 · 전체 건수 실제 값")
    void hugePageIsEmpty() {
        stockCheckStale(LocalDateTime.now().minusHours(2));

        client.get().uri(STUCK_PATH + "?kind=STOCK_CHECK_STALE&page=1073741824&size=2")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    /**
     * 결제 요청 전인 결제대기 주문을 주문 시각 orderedAt으로 만든다(기본 기준 5분 뒤가 since).
     */
    private String stockCheckStale(LocalDateTime orderedAt) {
        Order order = orderRepository.save(Order.place(BUYER_ID, List.of(new Order.RequestedItem(
                new OrderItem.ProductSnapshot(1L, 1L, SELLER_ID, "상품", null, null, 10_000L, 1L), true, null, 1,
                10_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now()));
        jdbcTemplate.update("update orders.orders set created_at = ?, payment_due_at = ? where id = ?",
            orderedAt, LocalDateTime.now().plusDays(1), order.getId());
        return String.valueOf(order.getId());
    }
}
