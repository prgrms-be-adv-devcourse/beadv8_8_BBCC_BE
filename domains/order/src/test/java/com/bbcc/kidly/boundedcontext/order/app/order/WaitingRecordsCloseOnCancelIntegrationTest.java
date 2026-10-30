package com.bbcc.kidly.boundedcontext.order.app.order;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.OrderRows;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.StockDeductionFailedCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문이 취소되면 그 주문의 재처리 대기(PENDING) 기록 — 이벤트 처리 실패 · 결제 반영 실패 — 을 같은 트랜잭션에서 닫는다(#598).
 * 닫힌 이유는 실패 코드 칸에 ORDER_CANCELLED. 관리자 재처리 · 종료가 다루는 자기 기록은 그 동작의 결과 그대로다.
 */
@IntegrationTest
class WaitingRecordsCloseOnCancelIntegrationTest {

    private static final Long BUYER_ID = 6_700L;
    private static final Long ADMIN_ID = 99L;
    private static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderEventFailureService failureService;

    @Autowired
    private PaymentRecoveryService recoveryService;

    @Autowired
    private OrderEventFailureRepository failureRepository;

    @Autowired
    private PaymentRecoveryRepository recoveryRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private OrderRows orderRows;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @Test
    @DisplayName("구매자 취소: 이벤트 처리 실패 · 결제 반영 실패 대기 기록이 CANCELLED · ORDER_CANCELLED로 닫히고, "
        + "결제 반영 실패는 상태 이력 한 줄, order.cancelled는 그대로 발행(결제 쪽 환불)")
    void buyerCancel() {
        Long orderId = place();
        Long failureId = pendingFailure(orderId, OrderEventFailure.EventType.STOCK_DEDUCTED);
        Long recoveryId = pendingRecovery(orderId);

        orderService.cancelByBuyer(BUYER_ID, orderId);

        assertClosedByCancel(failureId, recoveryId);
        assertThat(orderRows.historyRows(orderId))
            .anyMatch(row -> row.startsWith("PAYMENT_RECOVERY PENDING>CANCELLED ORDER_CANCELLED "));
        assertThat(recorder.events(OrderCancelledEvent.class)).singleElement()
            .satisfies(event -> assertThat(event.data().orderId()).isEqualTo(orderId));
    }

    @Test
    @DisplayName("결제 마감 만료 · 재고 부족 · 결제 실패 취소도 같은 지점에서 닫는다")
    void otherCancelPaths() {
        Long expired = place();
        Long expiredFailure = pendingFailure(expired, OrderEventFailure.EventType.STOCK_DEDUCTED);
        jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(1), expired);
        Long outOfStock = place();
        Long outOfStockFailure = pendingFailure(outOfStock, OrderEventFailure.EventType.STOCK_DEDUCTED);
        Long paymentFailed = place();
        Long paymentFailedRecovery = pendingRecovery(paymentFailed);

        orderService.cancelForPaymentExpiry(expired);
        orderService.cancelForStockDeductionFailure(new StockDeductionFailedCommand(outOfStock, List.of()));
        orderService.cancelForPaymentFailure(paymentFailed);

        assertThat(failureRow(expiredFailure)).containsEntry("status", "CANCELLED")
            .containsEntry("failure_code", ORDER_CANCELLED);
        assertThat(failureRow(outOfStockFailure)).containsEntry("status", "CANCELLED")
            .containsEntry("failure_code", ORDER_CANCELLED);
        assertThat(recoveryRow(paymentFailedRecovery)).containsEntry("status", "CANCELLED")
            .containsEntry("failure_code", ORDER_CANCELLED);
    }

    @Test
    @DisplayName("관리자 결제 반영 '취소로 종료': 자기 기록은 지금처럼(실패 코드 그대로), 같은 주문의 다른 대기 기록은 닫는다")
    void adminRecoveryCancel() {
        Long orderId = place();
        Long failureId = pendingFailure(orderId, OrderEventFailure.EventType.STOCK_DEDUCTED);
        Long recoveryId = pendingRecovery(orderId);

        recoveryService.cancel(recoveryId, ADMIN_ID);

        assertThat(recoveryRow(recoveryId)).containsEntry("status", "CANCELLED")
            .containsEntry("failure_code", "DataIntegrityViolationException");
        assertThat(failureRow(failureId)).containsEntry("status", "CANCELLED")
            .containsEntry("failure_code", ORDER_CANCELLED);
        assertThat(orderRows.historyRows(orderId)).filteredOn(row -> row.startsWith("PAYMENT_RECOVERY"))
            .as("자기 기록 이력은 관리자 종료 한 줄뿐").hasSize(1);
    }

    @Test
    @DisplayName("관리자 이벤트 처리 실패 재처리(결제 실패 → 주문 취소): 자기 기록은 지금처럼 RECOVERED, 같은 주문의 다른 대기 기록은 닫는다")
    void adminEventFailureRetry() {
        Long orderId = place();
        Long retried = pendingFailure(orderId, OrderEventFailure.EventType.PAYMENT_FAILED);
        Long other = pendingFailure(orderId, OrderEventFailure.EventType.STOCK_DEDUCTED);
        Long recoveryId = pendingRecovery(orderId);

        failureService.recover(retried, ADMIN_ID);

        assertThat(failureRow(retried)).containsEntry("status", "RECOVERED")
            .containsEntry("failure_code", "DataIntegrityViolationException");
        assertThat(failureRow(other)).containsEntry("status", "CANCELLED")
            .containsEntry("failure_code", ORDER_CANCELLED);
        assertThat(recoveryRow(recoveryId)).containsEntry("status", "CANCELLED")
            .containsEntry("failure_code", ORDER_CANCELLED);
    }

    @Test
    @DisplayName("취소가 아닌 변경(결제 요청)과 취소가 적용되지 않은 경우(이미 취소)에는 닫지 않고, 이미 닫힌 기록은 바꾸지 않는다")
    void notClosedOtherwise() {
        Long requested = place();
        Long requestedFailure = pendingFailure(requested, OrderEventFailure.EventType.PAYMENT_FAILED);
        orderService.requestPayment(requested);
        Long cancelled = place();
        orderService.cancelForPaymentFailure(cancelled);
        Long lateFailure = pendingFailure(cancelled, OrderEventFailure.EventType.STOCK_DEDUCTED);
        Long closed = place();
        Long recovered = pendingFailure(closed, OrderEventFailure.EventType.STOCK_DEDUCTED);
        jdbcTemplate.update("update orders.order_event_failure set status = 'RECOVERED' where id = ?", recovered);
        Long notRetryable = pendingRecovery(closed);
        jdbcTemplate.update("update orders.payment_recovery set status = 'NOT_RETRYABLE' where id = ?", notRetryable);

        orderService.cancelForPaymentFailure(cancelled);
        orderService.cancelByBuyer(BUYER_ID, closed);

        assertThat(failureRow(requestedFailure)).containsEntry("status", "PENDING");
        assertThat(failureRow(lateFailure)).as("이미 취소된 주문의 다시 취소(적용 안 됨)").containsEntry("status", "PENDING");
        assertThat(failureRow(recovered)).containsEntry("status", "RECOVERED")
            .containsEntry("failure_code", "DataIntegrityViolationException");
        assertThat(recoveryRow(notRetryable)).containsEntry("status", "NOT_RETRYABLE")
            .containsEntry("failure_code", "DataIntegrityViolationException");
    }

    @Test
    @DisplayName("다른 트랜잭션(관리자 재처리 · 종료)이 잡고 있는 기록은 기다리지 않고 건너뛰어, 취소는 바로 끝나고 그 기록은 그 동작에 맡긴다")
    void skipsLockedRecord() throws Exception {
        Long orderId = place();
        Long failureId = pendingFailure(orderId, OrderEventFailure.EventType.STOCK_DEDUCTED);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(
            status -> {
                jdbcTemplate.queryForList("select id from orders.order_event_failure where id = ? for update",
                    failureId);
                locked.countDown();
                await(release);
            }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> cancel = CompletableFuture.runAsync(
            () -> orderService.cancelByBuyer(BUYER_ID, orderId));
        cancel.get(Duration.ofSeconds(10).toSeconds(), TimeUnit.SECONDS);
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);

        assertThat(orderService.getOrder(BUYER_ID, orderId).status()).isEqualTo("CANCELLED");
        assertThat(failureRow(failureId)).containsEntry("status", "PENDING");
    }

    private void assertClosedByCancel(Long failureId, Long recoveryId) {
        assertThat(failureRow(failureId)).containsEntry("status", "CANCELLED")
            .containsEntry("failure_code", ORDER_CANCELLED);
        assertThat(recoveryRow(recoveryId)).containsEntry("status", "CANCELLED")
            .containsEntry("failure_code", ORDER_CANCELLED);
    }

    private Long place() {
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        return order.orderId();
    }

    private Long pendingFailure(Long orderId, OrderEventFailure.EventType eventType) {
        return failureRepository.save(OrderEventFailure.open(eventType, orderId, List.of(),
            "DataIntegrityViolationException", LocalDateTime.now())).getId();
    }

    private Long pendingRecovery(Long orderId) {
        return recoveryRepository.save(PaymentRecovery.open(orderId, 500L, BUYER_ID, 25_000L, List.of(),
            "DataIntegrityViolationException", true, LocalDateTime.now())).getId();
    }

    private Map<String, Object> failureRow(Long id) {
        return jdbcTemplate.queryForMap("select status, failure_code from orders.order_event_failure where id = ?",
            id);
    }

    private Map<String, Object> recoveryRow(Long id) {
        return jdbcTemplate.queryForMap("select status, failure_code from orders.payment_recovery where id = ?", id);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
