package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.admin.AdminStuckOrderController.STUCK_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnosisRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.domain.stuck.StuckOrderDiagnosis;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.global.security.TestMembers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 멈춘 주문 AI 진단 메모(#926) 흐름: 주기 작업 한 실행 → 메모 저장 → 관리자 멈춘 주문 목록의 aiMemo.
 * 주문 모듈 테스트는 가짜 제공자(fake, 외부 호출 없음)로 켜 둔다(application-test.yml). 주기는 길게 두고 테스트가 한 실행을 직접 부른다.
 */
@FunctionalTest
class StuckOrderDiagnosisApiTest {

    private static final Long ADMIN_ID = 99L;
    private static final String KIND = "STOCK_CHECK_STALE";

    @Autowired
    private RestTestClient client;

    @Autowired
    private StuckOrderDiagnosisScheduler scheduler;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private StuckOrderDiagnosisRepository diagnosisRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @Test
    @DisplayName("주문 · 취소 흐름은 AI를 부르지 않고, 주기 작업 한 실행이 새로 멈춘 주문의 메모를 남겨 관리자 목록 aiMemo에 붙는다")
    void diagnosesAndShowsMemo() {
        Long stale = staleOrder();
        Long cancelled = place().orderId();
        client.post().uri(ORDERS_PATH + "/" + cancelled + "/cancel")
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk();
        assertThat(count()).isZero();
        client.get().uri(STUCK_PATH + "?kind=" + KIND)
            .headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].orderId").isEqualTo(String.valueOf(stale))
            .jsonPath("$.content[0].aiMemo").value(memo -> assertThat(memo).isNull());

        scheduler.runOnWorker();

        Map<String, Object> row = jdbcTemplate.queryForMap("select * from orders.order_stuck_diagnosis");
        Long lastHistoryId = jdbcTemplate.queryForObject(
            "select max(id) from orders.order_status_history where order_id = ?", Long.class, stale);
        assertThat(row).containsEntry("order_id", stale)
            .containsEntry("stuck_kind", KIND)
            .containsEntry("history_mark", lastHistoryId)
            .containsEntry("status", "DIAGNOSED")
            .containsEntry("confidence", "LOW")
            .containsEntry("provider", "fake")
            .containsEntry("model", "fake-model");
        client.get().uri(STUCK_PATH + "?kind=" + KIND)
            .headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].aiMemo.summary").isEqualTo("가짜 진단: STOCK_CHECK_STALE 상태로 멈췄습니다")
            .jsonPath("$.content[0].aiMemo.recommendedAction").isEqualTo("관리자 목록에서 상태를 확인하세요")
            .jsonPath("$.content[0].aiMemo.confidence").isEqualTo("LOW")
            .jsonPath("$.content[0].aiMemo.provider").isEqualTo("fake")
            .jsonPath("$.content[0].aiMemo.model").isEqualTo("fake-model")
            .jsonPath("$.content[0].aiMemo.createdAt").exists();
    }

    @Test
    @DisplayName("상태가 그대로면 다시 실행해도 같은 주문을 다시 요청하지 않는다")
    void requestsOncePerState() {
        staleOrder();

        scheduler.runOnWorker();
        scheduler.runOnWorker();

        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패 메모만 있으면 aiMemo는 null이고 멈춘 주문 목록 · 요약은 그대로 200")
    void failedMemoDoesNotBreakAdminApi() {
        Long stale = staleOrder();
        Long mark = jdbcTemplate.queryForObject(
            "select max(id) from orders.order_status_history where order_id = ?", Long.class, stale);
        diagnosisRepository.save(StuckOrderDiagnosis.failed(new StuckOrderDiagnosis.Key(stale, KIND, mark),
            "TIMEOUT", new StuckOrderDiagnosis.Source("api.groq.com", "m", LocalDateTime.now())));

        scheduler.runOnWorker();

        assertThat(count()).isEqualTo(1);
        client.get().uri(STUCK_PATH + "?kind=" + KIND)
            .headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].orderId").isEqualTo(String.valueOf(stale))
            .jsonPath("$.content[0].aiMemo").value(memo -> assertThat(memo).isNull());
        client.get().uri(STUCK_PATH + "/summary")
            .headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$." + KIND).isEqualTo(1);
    }

    private long count() {
        return jdbcTemplate.queryForObject("select count(*) from orders.order_stuck_diagnosis", Long.class);
    }

    // 재고 확인이 오래 걸린 결제대기 주문(STOCK_CHECK_STALE)
    private Long staleOrder() {
        Long orderId = place().orderId();
        jdbcTemplate.update("update orders.orders set created_at = ?, payment_due_at = ? where id = ?",
            LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1), orderId);
        return orderId;
    }

    private OrderDetail place() {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }
}
