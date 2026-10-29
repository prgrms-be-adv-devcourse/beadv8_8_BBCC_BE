package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderEventFailureService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentFailures;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderEventFailureResult;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.web.OrderEventFailureRetry;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재고 차감됨 · 재고 차감 실패 · 결제 실패 처리 실패 보관 · 재처리 · 종료(#449, 실제 PostgreSQL). 테스트에 @Transactional을 붙이지 않는다.
 * 실패는 새 대역 없이 DB로 만든다: 한 주문 행만 막는 CHECK 제약(NOT VALID라 기존 행은 검사하지 않음)을 걸면 그 주문의 UPDATE가
 * DataIntegrityViolationException으로 실패한다. 주문 서비스가 처리 안에서 flush하므로 실패는 커밋 때가 아니라 리스너 안에서 나고
 * 보관된다. 제약을 지우면 같은 기록으로 재처리가 성공한다. 서로 막혀 멈추면 @Timeout으로 실패한다.
 */
@IntegrationTest
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class OrderEventFailureIntegrationTest {

    private static final Long BUYER_ID = 6_100L;
    private static final Long ADMIN_ID = 9_000L;
    private static final String BREAK = "test_break_order_update";
    private static final String FAILURE = "DataIntegrityViolationException";

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderEventFailureRetry failureRetry;

    @Autowired
    private OrderEventFailureService failureService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventFailureRepository failureRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @AfterEach
    void tearDown() {
        fixOrders();
    }

    @Test
    @DisplayName("주문 UPDATE가 실패하면(flush로 리스너 안에서 남) 세 이벤트 모두 주문은 그대로, 기록이 종류별 PENDING · 시도 1로 1건씩 남는다")
    void eachEventIsKept() {
        OrderDetail deducted = place(BUYER_ID);
        OrderDetail outOfStock = place(BUYER_ID + 1);
        OrderDetail paymentFailed = place(BUYER_ID + 2);
        breakOrders(deducted, outOfStock, paymentFailed);

        assertThatThrownBy(() -> eventDelivery.invoke(stockDeducted(deducted)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> eventDelivery.invoke(stockDeductionFailed(outOfStock)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> eventDelivery.invoke(paymentFailed(paymentFailed)))
            .isInstanceOf(DataIntegrityViolationException.class);

        Order deductedOrder = orderRepository.findById(deducted.orderId()).orElseThrow();
        assertThat(deductedOrder.getPaymentRequestedAt()).isNull();
        assertThat(recorder.events(OrderPaymentRequestedEvent.class)).isEmpty();
        for (OrderDetail order : List.of(deducted, outOfStock, paymentFailed)) {
            assertThat(orderRepository.findById(order.orderId()).orElseThrow().getStatus())
                .isEqualTo(Order.Status.PENDING_PAYMENT);
            assertThat(failureCount(order.orderId())).isEqualTo(1);
        }
        assertKept(failureOf(deducted, OrderEventFailure.EventType.STOCK_DEDUCTED));
        assertKept(failureOf(paymentFailed, OrderEventFailure.EventType.PAYMENT_FAILED));
        OrderEventFailureResult stock = failureOf(outOfStock, OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED);
        assertKept(stock);
        assertThat(stock.stockFailures()).containsExactly(new OrderEventFailureResult.StockFailure(
            outOfStock.items().get(0).orderItemId(), "OUT_OF_STOCK"));
        assertThat(recorder.events(OrderCancelledEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("같은 주문의 같은 이벤트가 두 번 실패하면 기록은 1건, 시도 2 · 마지막 시도 시각 갱신")
    void secondFailureCounts() {
        OrderDetail order = place(BUYER_ID);
        breakOrders(order);
        assertThatThrownBy(() -> eventDelivery.invoke(stockDeductionFailed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);
        LocalDateTime first = failureOf(order, OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED).lastAttemptedAt();

        assertThatThrownBy(() -> eventDelivery.invoke(stockDeductionFailed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(failureCount(order.orderId())).isEqualTo(1);
        OrderEventFailureResult failure = failureOf(order, OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED);
        assertThat(failure.attemptCount()).isEqualTo(2);
        assertThat(failure.lastAttemptedAt()).isAfterOrEqualTo(first);
        assertThat(failure.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("원인이 남아 있으면 재처리는 PENDING · 시도 +1, 고친 뒤 재처리하면 재고 부족 취소(주문상품 사유) · RECOVERED, 다시 눌러도 1회분")
    void retryStockDeductionFailed() {
        OrderDetail order = place(BUYER_ID);
        Long failureId = failAndGetId(order, OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED);

        OrderEventFailureResult stillBroken = failureRetry.retry(failureId, ADMIN_ID);
        assertThat(stillBroken.status()).isEqualTo("PENDING");
        assertThat(stillBroken.attemptCount()).isEqualTo(2);
        assertThat(stillBroken.failureCode()).isEqualTo(FAILURE);

        fixOrders();
        OrderEventFailureResult recovered = failureRetry.retry(failureId, ADMIN_ID);
        OrderEventFailureResult again = failureRetry.retry(failureId, ADMIN_ID);

        assertThat(recovered.status()).isEqualTo("RECOVERED");
        assertThat(again.status()).isEqualTo("RECOVERED");
        assertThat(again.attemptCount()).isEqualTo(2);
        Order stored = orderRepository.findById(order.orderId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(stored.getCancelReason()).isEqualTo(Order.CancelReason.OUT_OF_STOCK);
        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).items().get(0).unavailableReason())
            .isEqualTo("OUT_OF_STOCK");
        assertThat(cancelledEventsOf(order.orderId())).isEqualTo(1);
        assertThat(adminHistoryCount(order.orderId())).isEqualTo(1);
    }

    @Test
    @DisplayName("재고 차감됨 재처리는 결제 요청을 한 번만 발행하고 RECOVERED, 다시 눌러도 결제 요청 추가 없음")
    void retryStockDeducted() {
        OrderDetail order = place(BUYER_ID);
        Long failureId = failAndGetId(order, OrderEventFailure.EventType.STOCK_DEDUCTED);
        fixOrders();

        assertThat(failureRetry.retry(failureId, ADMIN_ID).status()).isEqualTo("RECOVERED");
        assertThat(failureRetry.retry(failureId, ADMIN_ID).status()).isEqualTo("RECOVERED");

        assertThat(recorder.events(OrderPaymentRequestedEvent.class))
            .filteredOn(event -> event.data().orderId().equals(order.orderId())).hasSize(1);
    }

    @Test
    @DisplayName("결제 실패 재처리는 결제 실패 취소 · order.cancelled 1건 · RECOVERED")
    void retryPaymentFailed() {
        OrderDetail order = place(BUYER_ID);
        Long failureId = failAndGetId(order, OrderEventFailure.EventType.PAYMENT_FAILED);
        fixOrders();

        assertThat(failureRetry.retry(failureId, ADMIN_ID).status()).isEqualTo("RECOVERED");
        assertThat(failureRetry.retry(failureId, ADMIN_ID).status()).isEqualTo("RECOVERED");

        Order stored = orderRepository.findById(order.orderId()).orElseThrow();
        assertThat(stored.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_FAILED);
        assertThat(cancelledEventsOf(order.orderId())).isEqualTo(1);
    }

    @Test
    @DisplayName("주문이 이미 만료 취소됐으면 재처리는 기록만 CANCELLED, 주문 사유는 PAYMENT_EXPIRED 그대로 · 추가 order.cancelled 없음")
    void alreadyExpiredOrder() {
        OrderDetail order = place(BUYER_ID);
        Long failureId = failAndGetId(order, OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED);
        fixOrders();
        jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(1), order.orderId());
        orderService.cancelForPaymentExpiry(order.orderId());

        OrderEventFailureResult result = failureRetry.retry(failureId, ADMIN_ID);

        assertThat(result.status()).isEqualTo("CANCELLED");
        Order stored = orderRepository.findById(order.orderId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(stored.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_EXPIRED);
        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).items().get(0).unavailableReason()).isNull();
        assertThat(cancelledEventsOf(order.orderId())).isEqualTo(1);
    }

    @Test
    @DisplayName("종료는 기록만 CANCELLED, 주문은 결제대기 그대로 · 이벤트 없음. 다시 눌러도 1회분, 그 뒤 재처리는 409")
    void cancelClosesRecordOnly() {
        OrderDetail order = place(BUYER_ID);
        Long failureId = failAndGetId(order, OrderEventFailure.EventType.PAYMENT_FAILED);
        fixOrders();

        assertThat(failureService.cancel(failureId).status()).isEqualTo("CANCELLED");
        OrderEventFailureResult again = failureService.cancel(failureId);

        assertThat(again.status()).isEqualTo("CANCELLED");
        assertThat(again.attemptCount()).isEqualTo(1);
        assertThat(orderRepository.findById(order.orderId()).orElseThrow().getStatus())
            .isEqualTo(Order.Status.PENDING_PAYMENT);
        assertThat(cancelledEventsOf(order.orderId())).isZero();
        assertThatThrownBy(() -> failureRetry.retry(failureId, ADMIN_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.EVENT_FAILURE_CLOSED));
    }

    @Test
    @DisplayName("재처리 두 번이 동시에 와도 1회분: RECOVERED · order.cancelled 1건 · 관리자 취소 이력 1건, 늦은 쪽도 RECOVERED")
    void concurrentRetries() throws InterruptedException {
        for (int round = 1; round <= 5; round++) {
            Long buyerId = 7_100L + round;
            OrderDetail order = place(buyerId);
            Long failureId = failAndGetId(order, OrderEventFailure.EventType.PAYMENT_FAILED);
            fixOrders();
            List<OrderEventFailureResult> results = new CopyOnWriteArrayList<>();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> results.add(failureRetry.retry(failureId, ADMIN_ID)),
                () -> results.add(failureRetry.retry(failureId, ADMIN_ID))),
                PaymentFailures::isLostConcurrentDuplicate);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(results).as("%d회차 응답", round).hasSize(2)
                .extracting(OrderEventFailureResult::status).containsOnly("RECOVERED");
            assertThat(cancelledEventsOf(order.orderId())).as("%d회차 취소 이벤트", round).isEqualTo(1);
            assertThat(adminHistoryCount(order.orderId())).as("%d회차 관리자 이력", round).isEqualTo(1);
            assertThat(failureOf(order, OrderEventFailure.EventType.PAYMENT_FAILED).attemptCount())
                .as("%d회차 시도", round).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("재처리와 종료가 동시에 오면 한쪽만 반영: 복구(주문 취소 · 이벤트 1) 또는 종료(주문 결제대기 · 이벤트 0), 진 쪽은 409")
    void concurrentRetryAndCancel() throws InterruptedException {
        List<String> outcomes = new ArrayList<>();
        for (int round = 1; round <= 5; round++) {
            Long buyerId = 8_100L + round;
            OrderDetail order = place(buyerId);
            Long failureId = failAndGetId(order, OrderEventFailure.EventType.PAYMENT_FAILED);
            fixOrders();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> failureRetry.retry(failureId, ADMIN_ID),
                () -> failureService.cancel(failureId)),
                OrderEventFailureIntegrationTest::isLostOrClosed);

            assertThat(outcome.unexpected()).as("%d회차 예외 %s", round, outcome.unexpected()).isEmpty();
            String status = failureOf(order, OrderEventFailure.EventType.PAYMENT_FAILED).status();
            Order.Status orderStatus = orderRepository.findById(order.orderId()).orElseThrow().getStatus();
            if (status.equals("RECOVERED")) {
                assertThat(orderStatus).as("%d회차", round).isEqualTo(Order.Status.CANCELLED);
                assertThat(cancelledEventsOf(order.orderId())).as("%d회차", round).isEqualTo(1);
            } else {
                assertThat(status).as("%d회차", round).isEqualTo("CANCELLED");
                assertThat(orderStatus).as("%d회차", round).isEqualTo(Order.Status.PENDING_PAYMENT);
                assertThat(cancelledEventsOf(order.orderId())).as("%d회차", round).isZero();
            }
            outcomes.add(status);
        }
        assertThat(outcomes).hasSize(5);
    }

    @Test
    @DisplayName("재처리 · 종료가 기록을 잠그고 있으면 리스너의 실패 보관은 기다리지 않고 바로 실패한다(원래 예외 + suppressed, 기록 그대로)")
    void recordFailureDoesNotWaitForLockedRecord() throws Exception {
        OrderDetail order = place(BUYER_ID);
        Long failureId = failAndGetId(order, OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
                failureRepository.findByIdForUpdate(failureId).orElseThrow();
                locked.countDown();
                awaitQuietly(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).as("A가 기록을 잠금").isTrue();

            Future<?> listener = executor.submit(
                () -> eventDelivery.invoke(stockDeductionFailed(order)));
            assertThatThrownBy(() -> listener.get(2, TimeUnit.SECONDS)).as("리스너는 멈추지 않음")
                .isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(error -> assertThat(error.getSuppressed()).singleElement()
                    .isInstanceOf(PessimisticLockingFailureException.class));

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
        OrderEventFailureResult failure = failureOf(order, OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED);
        assertThat(failure.attemptCount()).isEqualTo(1);
        assertThat(failure.status()).isEqualTo("PENDING");
    }

    private static boolean isLostOrClosed(Throwable error) {
        if (PaymentFailures.isLostConcurrentDuplicate(error)) {
            return true;
        }
        return error instanceof BusinessException business
            && business.getErrorCode() == OrderErrorCode.EVENT_FAILURE_CLOSED;
    }

    private static void assertKept(OrderEventFailureResult failure) {
        assertThat(failure.status()).isEqualTo("PENDING");
        assertThat(failure.attemptCount()).isEqualTo(1);
        assertThat(failure.failureCode()).isEqualTo(FAILURE);
    }

    /**
     * 주문 UPDATE를 막고 그 종류의 이벤트를 한 번 보내 실패를 보관한 뒤 기록 ID를 돌려준다(막은 상태는 그대로).
     */
    private Long failAndGetId(OrderDetail order, OrderEventFailure.EventType eventType) {
        breakOrders(order);
        Consumer<OrderDetail> send = switch (eventType) {
            case STOCK_DEDUCTED -> target -> eventDelivery.invoke(stockDeducted(target));
            case STOCK_DEDUCTION_FAILED -> target -> eventDelivery.invoke(
                stockDeductionFailed(target));
            case PAYMENT_FAILED -> target -> eventDelivery.invoke(paymentFailed(target));
        };
        assertThatThrownBy(() -> send.accept(order)).isInstanceOf(DataIntegrityViolationException.class);
        return failureOf(order, eventType).failureId();
    }

    private OrderDetail place(Long buyerId) {
        return orderService.place(buyerId, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    // NOT VALID라 기존 행 검사 없이 걸린다. 그 주문 행을 바꾸는 UPDATE만 실패한다
    private void breakOrders(OrderDetail... orders) {
        fixOrders();
        String ids = String.join(", ", Arrays.stream(orders)
            .map(order -> String.valueOf(order.orderId())).toList());
        jdbcTemplate.execute("alter table orders.orders add constraint " + BREAK
            + " check (id not in (" + ids + ")) not valid");
    }

    private void fixOrders() {
        jdbcTemplate.execute("alter table orders.orders drop constraint if exists " + BREAK);
    }

    private OrderEventFailureResult failureOf(OrderDetail order, OrderEventFailure.EventType eventType) {
        OrderEventFailure failure = failureRepository.findByOrderIdAndEventType(order.orderId(), eventType)
            .orElseThrow();
        return failureService.getFailure(failure.getId());
    }

    private long failureCount(Long orderId) {
        return jdbcTemplate.queryForObject("select count(*) from orders.order_event_failure where order_id = ?",
            Long.class, orderId);
    }

    private long adminHistoryCount(Long orderId) {
        return jdbcTemplate.queryForObject("select count(*) from orders.order_status_history "
            + "where order_id = ? and actor_type = 'ADMIN' and target_type = 'ORDER'", Long.class, orderId);
    }

    private long cancelledEventsOf(Long orderId) {
        return recorder.events(OrderCancelledEvent.class).stream()
            .filter(event -> event.data().orderId().equals(orderId))
            .count();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("잠금 해제 신호를 기다리다 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static StockDeductedEvent stockDeducted(OrderDetail order) {
        return new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(order.orderId()));
    }

    private static StockDeductionFailedEvent stockDeductionFailed(OrderDetail order) {
        return new StockDeductionFailedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductionFailedEvent.Data(order.orderId(), StockDeductionFailureCode.OUT_OF_STOCK, List.of(
                new StockDeductionFailedEvent.Failure(order.items().get(0).orderItemId(),
                    StockDeductionFailureCode.OUT_OF_STOCK))));
    }

    private static PaymentFailedEvent paymentFailed(OrderDetail order) {
        LocalDateTime now = LocalDateTime.now();
        return new PaymentFailedEvent(UUID.randomUUID(), now, new PaymentFailedEvent.Data(null, order.orderId(),
            BUYER_ID, order.totalAmount(), PaymentFailureCode.WALLET_NOT_FOUND, now));
    }
}
