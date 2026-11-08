package com.bbcc.kidly.boundedcontext.order.out.ai;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.config.StuckOrderDiagnosisProperties;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnosisRepository;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderDiagnosisService;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderService;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 멈춘 주문 AI 진단(#926)의 개인정보: 배송지 · 수령인 · 연락처 · 자녀가 든 실제 주문으로 서비스가 만든 진단 요청을
 * 제공자 무관 프롬프트로 직렬화한 글(AI로 나가는 본문)에 개인정보 값 · 칸이 없는지. 진단 구현만 요청을 받아 두는 것으로 바꾸고
 * 나머지(멈춘 주문 조회 · 이력 · 실패 · 복구 기록 · 메모 저장)는 실제 빈이다.
 */
@IntegrationTest
class StuckOrderDiagnosisPrivacyIntegrationTest {

    private static final String RECIPIENT = "김수령";
    private static final String PHONE = "010-9876-5432";
    private static final String ZIP = "63309";
    private static final String ADDRESS = "제주시 비밀로 77";

    @Autowired
    private StuckOrderService stuckOrderService;

    @Autowired
    private StuckOrderDiagnosisRepository diagnosisRepository;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    @Autowired
    private OrderEventFailureRepository failureRepository;

    @Autowired
    private PaymentRecoveryRepository recoveryRepository;

    @Autowired
    private StuckOrderDiagnosisProperties properties;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private OrderOutboxRepository outboxRepository;

    @Autowired
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("실제 주문(배송지 · 수령인 · 연락처 · 자녀 주문상품)으로 만든 진단 요청의 프롬프트에 개인정보 값 · 칸이 없다")
    void promptFromRealOrderHasNoPersonalData() {
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, 1L, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress(RECIPIENT, PHONE, ZIP, ADDRESS)));
        jdbcTemplate.update("update orders.orders set created_at = ?, payment_due_at = ? where id = ?",
            LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1), order.orderId());
        failureRepository.save(OrderEventFailure.open(OrderEventFailure.EventType.PAYMENT_FAILED, order.orderId(),
            List.of(), "WALLET_NOT_FOUND", LocalDateTime.now()));
        recoveryRepository.save(PaymentRecovery.open(order.orderId(), 500L, BUYER_ID, order.totalAmount(),
            List.of(new PaymentRecovery.PaidItem(order.items().getFirst().orderItemId(), order.totalAmount())),
            "member-mismatch", true, LocalDateTime.now()));
        List<StuckOrderDiagnoser.Request> requests = new ArrayList<>();
        StuckOrderDiagnoser capturing = new StuckOrderDiagnoser() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Diagnosis diagnose(Request request) {
                requests.add(request);
                return Diagnosis.diagnosed("요약", "조치", Confidence.LOW, "test", "test");
            }
        };

        new StuckOrderDiagnosisService(stuckOrderService, capturing, diagnosisRepository, historyRepository,
            failureRepository, recoveryRepository, properties).diagnoseNew(LocalDateTime.now());

        assertThat(requests).singleElement().satisfies(request -> {
            String prompt = jsonMapper.writeValueAsString(new AiDiagnosisPrompt(jsonMapper).messages(request));
            assertThat(prompt).contains("STOCK_CHECK_STALE", "PENDING_PAYMENT", "WALLET_NOT_FOUND", "member-mismatch");
            assertThat(prompt).doesNotContain(RECIPIENT, PHONE, ZIP, ADDRESS, "115.0", "20.0");
            assertThat(prompt).doesNotContain("recipient", "phone", "zipCode", "address", "nickname", "child",
                "memberId", "actorId", "height", "weight");
        });
    }

    @Test
    @DisplayName("자유 문구 칸(아웃박스 lastError · 이력 reason/source · 실패 코드)에 개인정보를 넣어도 AI 요청 글에는 없고, "
        + "실제 코드(member-mismatch · 예외 이름 · enum 사유 · 경로 틀 · 이벤트 타입)는 그대로 나간다")
    void freeTextIsFilteredFromPrompt() {
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, 1L, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress(RECIPIENT, PHONE, ZIP, ADDRESS)));
        Long orderId = order.orderId();
        jdbcTemplate.update("update orders.orders set created_at = ?, payment_due_at = ? where id = ?",
            LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1), orderId);
        Long outboxId = jdbcTemplate.queryForObject("select min(id) from orders.order_outbox where message_key = ?",
            Long.class, String.valueOf(orderId));
        transactions.executeWithoutResult(status -> outboxRepository.quarantine(outboxId,
            "RecordTooLargeException: " + RECIPIENT + " 01098765432", LocalDateTime.now()));
        history(orderId, "hong-gildong", "JohnSmith");
        history(orderId, RECIPIENT + " 요청", "전화 " + PHONE);
        history(orderId, "BUYER_CANCELLED", "POST /api/v1/orders/{orderId}/cancel");
        failureRepository.save(OrderEventFailure.open(OrderEventFailure.EventType.PAYMENT_FAILED, orderId,
            List.of(), "연락 010-9876-5432", LocalDateTime.now()));
        failureRepository.save(OrderEventFailure.open(OrderEventFailure.EventType.STOCK_DEDUCTED, orderId,
            List.of(), "DataIntegrityViolationException", LocalDateTime.now()));
        recoveryRepository.save(PaymentRecovery.open(orderId, 500L, BUYER_ID, order.totalAmount(),
            List.of(new PaymentRecovery.PaidItem(order.items().getFirst().orderItemId(), order.totalAmount())),
            "member-mismatch", true, LocalDateTime.now()));

        List<String> prompts = promptsOf(diagnoseCapturing());

        assertThat(prompts).isNotEmpty().allSatisfy(prompt -> assertThat(prompt).doesNotContain(RECIPIENT, PHONE,
            "01098765432", "010-9876-5432", "hong-gildong", "JohnSmith", "요청", "전화", "연락", ADDRESS));
        String all = String.join("\n", prompts);
        assertThat(all).contains("member-mismatch", "DataIntegrityViolationException", "BUYER_CANCELLED",
            "POST /api/v1/orders/{orderId}/cancel", "RecordTooLargeException", "OUTBOX_STUCK", "STOCK_CHECK_STALE");
        assertThat(all).doesNotContain("null");
    }

    private void history(Long orderId, String reason, String source) {
        jdbcTemplate.update("insert into orders.order_status_history (order_id, target_type, target_id, from_status,"
                + " to_status, reason, actor_type, actor_id, source, occurred_at, created_at, updated_at)"
                + " values (?, 'ORDER', ?, 'PENDING_PAYMENT', 'PENDING_PAYMENT', ?, 'ADMIN', 99, ?,"
                + " now(), now(), now())",
            orderId, orderId, reason, source);
    }

    private List<StuckOrderDiagnoser.Request> diagnoseCapturing() {
        List<StuckOrderDiagnoser.Request> requests = new ArrayList<>();
        StuckOrderDiagnoser capturing = new StuckOrderDiagnoser() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Diagnosis diagnose(Request request) {
                requests.add(request);
                return Diagnosis.diagnosed("요약", "조치", Confidence.LOW, "test", "test");
            }
        };
        new StuckOrderDiagnosisService(stuckOrderService, capturing, diagnosisRepository, historyRepository,
            failureRepository, recoveryRepository, properties).diagnoseNew(LocalDateTime.now());
        return requests;
    }

    private List<String> promptsOf(List<StuckOrderDiagnoser.Request> requests) {
        AiDiagnosisPrompt prompt = new AiDiagnosisPrompt(jsonMapper);
        return requests.stream().map(request -> prompt.user(request)).toList();
    }
}
