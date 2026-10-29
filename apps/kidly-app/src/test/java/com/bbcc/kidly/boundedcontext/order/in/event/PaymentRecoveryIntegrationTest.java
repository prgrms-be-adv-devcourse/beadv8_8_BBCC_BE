package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentFailures;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentRecoveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PaymentRecoveryResult;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.web.PaymentRecoveryRetry;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 완료 반영 실패 보관 · 재처리 · 취소로 종료(#353, 실제 PostgreSQL). 테스트에 @Transactional을 붙이지 않는다.
 * 실패는 새 대역 없이 데이터로 만든다: 주문 뒤 한 주문상품의 seller_id를 NULL로 바꾸면 배송 생성이
 * delivery.seller_id NOT NULL 위반(DataIntegrityViolationException, 배송 유니크 아님 = 진 쪽 아님)으로 실패한다.
 * seller_id를 되돌리면 같은 주문으로 재처리가 성공한다. 기록 저장은 리스너 트랜잭션이 주문 행을 바꾼 채 열려 있을 때
 * 새 트랜잭션에서 하므로, 서로 막혀 멈추면 @Timeout으로 실패한다.
 */
@IntegrationTest
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class PaymentRecoveryIntegrationTest {

    private static final Long BUYER_ID = 6_000L;
    private static final Long OTHER_SELLER_ID = 3L;
    private static final Long PAYMENT_ID = 500L;
    private static final Long ADMIN_ID = 9_000L;
    private static final String FAILURE = "DataIntegrityViolationException";

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private PaymentRecoveryRetry recoveryRetry;

    @Autowired
    private PaymentRecoveryService recoveryService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private PaymentRecoveryRepository recoveryRepository;

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
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("배송 생성이 실패하면 주문 · 배송 · 장바구니는 되돌려지고, 받은 결제 완료가 PENDING · 시도 1로 남아 만료 대상에서 빠진다")
    void failureIsKept() {
        OrderDetail order = placeWithCart(BUYER_ID);
        breakSeller(order);

        assertThatThrownBy(() -> eventDelivery.invoke(completed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);

        Order stored = orderRepository.findById(order.orderId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Order.Status.PENDING_PAYMENT);
        assertThat(stored.getPaidAt()).isNull();
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).isEmpty();
        assertThat(cartItemRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID)).hasSize(2);
        PaymentRecovery recovery = recoveryOf(order);
        assertThat(recovery.getStatus()).isEqualTo(PaymentRecovery.Status.PENDING);
        assertThat(recovery.getAttemptCount()).isEqualTo(1);
        assertThat(recovery.getFailureCode()).isEqualTo(FAILURE);
        assertThat(recovery.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(recovery.getMemberId()).isEqualTo(BUYER_ID);
        assertThat(recovery.getAmount()).isEqualTo(order.totalAmount());
        assertThat(itemsOf(recovery)).containsExactlyElementsOf(order.items().stream()
            .map(item -> new PaymentRecoveryResult.Item(item.orderItemId(), item.amount())).toList());

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?", now.minusMinutes(1),
            order.orderId());
        assertThat(orderRepository.findOverduePaymentOrderIds(Order.Status.PENDING_PAYMENT, now, 0L, Limit.of(10)))
            .doesNotContain(order.orderId());
    }

    @Test
    @DisplayName("같은 주문이 두 번 실패하면 기록은 1건, 시도 2 · 마지막 시도 시각 갱신")
    void secondFailureCounts() {
        OrderDetail order = placeWithCart(BUYER_ID);
        breakSeller(order);
        assertThatThrownBy(() -> eventDelivery.invoke(completed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);
        LocalDateTime firstAttempt = recoveryOf(order).getLastAttemptedAt();

        assertThatThrownBy(() -> eventDelivery.invoke(completed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.payment_recovery where order_id = ?",
            Long.class, order.orderId())).isEqualTo(1L);
        PaymentRecovery recovery = recoveryOf(order);
        assertThat(recovery.getAttemptCount()).isEqualTo(2);
        assertThat(recovery.getLastAttemptedAt()).isAfterOrEqualTo(firstAttempt);
        assertThat(recovery.getStatus()).isEqualTo(PaymentRecovery.Status.PENDING);
    }

    @Test
    @DisplayName("원인을 고친 뒤 재처리하면 결제완료 · 판매자별 배송 · 장바구니 정리 · RECOVERED, 다시 눌러도 그대로(배송 중복 없음)")
    void retryRecovers() {
        OrderDetail order = placeWithCart(BUYER_ID);
        breakSeller(order);
        assertThatThrownBy(() -> eventDelivery.invoke(completed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);
        Long recoveryId = recoveryOf(order).getId();

        PaymentRecoveryResult stillBroken = recoveryRetry.retry(recoveryId, ADMIN_ID);
        assertThat(stillBroken.status()).isEqualTo("PENDING");
        assertThat(stillBroken.attemptCount()).isEqualTo(2);
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).isEmpty();

        fixSeller(order);
        PaymentRecoveryResult recovered = recoveryRetry.retry(recoveryId, ADMIN_ID);
        PaymentRecoveryResult again = recoveryRetry.retry(recoveryId, ADMIN_ID);

        assertThat(recovered.status()).isEqualTo("RECOVERED");
        assertThat(again.status()).isEqualTo("RECOVERED");
        Order stored = orderRepository.findById(order.orderId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Order.Status.PAID);
        assertThat(stored.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).hasSize(2);
        assertThat(cartItemRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID)).isEmpty();
    }

    @Test
    @DisplayName("취소로 종료하면 주문은 결제 실패 취소 · order.cancelled 1건 · 기록 CANCELLED, 다시 눌러도 이벤트는 1건")
    void cancelCloses() {
        OrderDetail order = placeWithCart(BUYER_ID);
        breakSeller(order);
        assertThatThrownBy(() -> eventDelivery.invoke(completed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);
        Long recoveryId = recoveryOf(order).getId();

        assertThat(recoveryService.cancel(recoveryId, ADMIN_ID).status()).isEqualTo("CANCELLED");
        assertThat(recoveryService.cancel(recoveryId, ADMIN_ID).status()).isEqualTo("CANCELLED");

        Order stored = orderRepository.findById(order.orderId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(stored.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_FAILED);
        assertThat(cancelledEventsOf(order.orderId())).isEqualTo(1);
    }

    @Test
    @DisplayName("기록 저장과 만료가 경합해 주문이 이미 취소됐으면 재처리 · 취소 모두 기록만 CANCELLED, 결제완료 · 추가 order.cancelled 없음")
    void alreadyCancelledOrder() {
        OrderDetail first = placeWithCart(BUYER_ID);
        OrderDetail second = placeWithCart(BUYER_ID + 1);
        for (OrderDetail order : List.of(first, second)) {
            breakSeller(order);
            assertThatThrownBy(() -> eventDelivery.invoke(completed(order, buyerOf(order))))
                .isInstanceOf(DataIntegrityViolationException.class);
            orderService.cancelForPaymentFailure(order.orderId());
        }

        assertThat(recoveryRetry.retry(recoveryOf(first).getId(), ADMIN_ID).status()).isEqualTo("CANCELLED");
        assertThat(recoveryService.cancel(recoveryOf(second).getId(), ADMIN_ID).status()).isEqualTo("CANCELLED");

        for (OrderDetail order : List.of(first, second)) {
            assertThat(orderRepository.findById(order.orderId()).orElseThrow().getStatus())
                .isEqualTo(Order.Status.CANCELLED);
            assertThat(cancelledEventsOf(order.orderId())).isEqualTo(1);
            assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).isEmpty();
        }
    }

    @Test
    @DisplayName("결제 ID · 회원이 없거나 주문상품 칸이 비어 있는 결제 완료도 그대로 보관한다(불일치라 NOT_RETRYABLE)")
    void keepsMissingFields() {
        OrderDetail noMember = placeWithCart(BUYER_ID);
        OrderDetail noItems = placeWithCart(BUYER_ID + 1);
        OrderDetail nullItemId = placeWithCart(BUYER_ID + 2);
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> eventDelivery.invoke(new PaymentCompletedEvent(UUID.randomUUID(),
            now, new PaymentCompletedEvent.Data(null, noMember.orderId(), null, noMember.totalAmount(),
            itemsOf(noMember), null)))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> eventDelivery.invoke(new PaymentCompletedEvent(UUID.randomUUID(),
            now, new PaymentCompletedEvent.Data(PAYMENT_ID, noItems.orderId(), BUYER_ID + 1, noItems.totalAmount(),
            null, now)))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> eventDelivery.invoke(new PaymentCompletedEvent(UUID.randomUUID(),
            now, new PaymentCompletedEvent.Data(PAYMENT_ID, nullItemId.orderId(), BUYER_ID + 2,
            nullItemId.totalAmount(), List.of(new PaymentCompletedEvent.Item(null, nullItemId.totalAmount())),
            now)))).isInstanceOf(RuntimeException.class);

        PaymentRecovery missingMember = recoveryOf(noMember);
        assertThat(missingMember.getStatus()).isEqualTo(PaymentRecovery.Status.NOT_RETRYABLE);
        assertThat(missingMember.getFailureCode()).isEqualTo("member-mismatch");
        assertThat(missingMember.getPaymentId()).isNull();
        assertThat(missingMember.getMemberId()).isNull();
        PaymentRecovery missingItems = recoveryOf(noItems);
        assertThat(missingItems.getFailureCode()).isEqualTo("item-missing");
        assertThat(itemsOf(missingItems)).isEmpty();
        PaymentRecovery missingItemId = recoveryOf(nullItemId);
        assertThat(missingItemId.getStatus()).isEqualTo(PaymentRecovery.Status.NOT_RETRYABLE);
        assertThat(itemsOf(missingItemId))
            .containsExactly(new PaymentRecoveryResult.Item(null, nullItemId.totalAmount()));
    }

    @Test
    @DisplayName("재처리 두 번이 동시에 와도 RECOVERED 한 번 · 판매자별 배송 1건씩 · 장바구니 정리, 진 쪽은 409로 끝나고 되돌리지 않는다")
    void concurrentRetries() throws InterruptedException {
        for (int round = 1; round <= 5; round++) {
            Long buyerId = 7_000L + round;
            OrderDetail order = placeWithCart(buyerId);
            breakSeller(order);
            assertThatThrownBy(() -> eventDelivery.invoke(completed(order, buyerId)))
                .isInstanceOf(DataIntegrityViolationException.class);
            fixSeller(order);
            Long recoveryId = recoveryOf(order).getId();
            List<PaymentRecoveryResult> results = new CopyOnWriteArrayList<>();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> results.add(recoveryRetry.retry(recoveryId, ADMIN_ID)),
                () -> results.add(recoveryRetry.retry(recoveryId, ADMIN_ID))),
                PaymentFailures::isLostConcurrentDuplicate);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(results).as("%d회차 응답", round).isNotEmpty()
                .extracting(PaymentRecoveryResult::status).containsOnly("RECOVERED");
            assertThat(recoveryOf(order).getStatus()).as("%d회차 기록", round)
                .isEqualTo(PaymentRecovery.Status.RECOVERED);
            assertThat(recoveryOf(order).getAttemptCount()).as("%d회차 시도", round).isEqualTo(1);
            assertThat(orderRepository.findById(order.orderId()).orElseThrow().getStatus())
                .isEqualTo(Order.Status.PAID);
            assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).as("%d회차 배송", round)
                .hasSize(2);
            assertThat(historyCount(order.orderId(), "PAYMENT_RECOVERY", "RECOVERED")).as("%d회차 복구 이력", round)
                .isEqualTo(1);
            assertThat(historyCount(order.orderId(), "ORDER", "PAID")).as("%d회차 결제완료 이력", round).isEqualTo(1);
            assertThat(historyCount(order.orderId(), "DELIVERY", "READY")).as("%d회차 배송 생성 이력", round).isEqualTo(2);
            assertThat(cartItemRepository.findAllByMemberIdOrderByIdDesc(buyerId)).isEmpty();
        }
    }

    @Test
    @DisplayName("재처리와 취소가 동시에 오면 한쪽만 반영: 복구(결제완료 · 취소 이벤트 0) 또는 취소(주문 취소 · 이벤트 1)뿐")
    void concurrentRetryAndCancel() throws InterruptedException {
        List<String> outcomes = new ArrayList<>();
        for (int round = 1; round <= 5; round++) {
            Long buyerId = 8_000L + round;
            OrderDetail order = placeWithCart(buyerId);
            breakSeller(order);
            assertThatThrownBy(() -> eventDelivery.invoke(completed(order, buyerId)))
                .isInstanceOf(DataIntegrityViolationException.class);
            fixSeller(order);
            Long recoveryId = recoveryOf(order).getId();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> recoveryRetry.retry(recoveryId, ADMIN_ID),
                () -> recoveryService.cancel(recoveryId, ADMIN_ID)),
                PaymentRecoveryIntegrationTest::isLostOrClosed);

            int currentRound = round;
            assertThat(outcome.unexpected()).as(() -> diagnose(currentRound, order, outcome)).isEmpty();
            PaymentRecovery.Status recovery = recoveryOf(order).getStatus();
            Order.Status orderStatus = orderRepository.findById(order.orderId()).orElseThrow().getStatus();
            long cancelled = cancelledEventsOf(order.orderId());
            if (recovery == PaymentRecovery.Status.RECOVERED) {
                assertThat(orderStatus).as("%d회차", round).isEqualTo(Order.Status.PAID);
                assertThat(cancelled).as("%d회차", round).isZero();
                assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).hasSize(2);
            } else {
                assertThat(recovery).as("%d회차", round).isEqualTo(PaymentRecovery.Status.CANCELLED);
                assertThat(orderStatus).as("%d회차", round).isEqualTo(Order.Status.CANCELLED);
                assertThat(cancelled).as("%d회차", round).isEqualTo(1);
                assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).isEmpty();
            }
            outcomes.add(recovery.name());
        }
        assertThat(outcomes).hasSize(5);
    }

    @Test
    @DisplayName("다른 트랜잭션이 기록을 잠근 동안 취소는 기다렸다가, 그쪽이 복구로 닫고 커밋하면 409 RECOVERY_CLOSED로 끝난다(주문 취소 없음)")
    void cancelWaitsForRecordLock() throws Exception {
        OrderDetail order = failDelivery(BUYER_ID);
        Long recoveryId = recoveryOf(order).getId();

        Throwable cancelError = whileRecordLocked(recoveryId,
            recovery -> recovery.recover(LocalDateTime.now()),
            () -> recoveryService.cancel(recoveryId, ADMIN_ID));

        assertThat(cancelError).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.RECOVERY_CLOSED));
        assertThat(orderRepository.findById(order.orderId()).orElseThrow().getStatus())
            .isEqualTo(Order.Status.PENDING_PAYMENT);
        assertThat(cancelledEventsOf(order.orderId())).isZero();
    }

    @Test
    @DisplayName("다른 트랜잭션이 기록을 잠근 동안 재처리는 기다렸다가, 그쪽이 주문을 취소하고 기록을 닫으면 409 RECOVERY_CLOSED로 끝난다(배송 0)")
    void retryWaitsForRecordLock() throws Exception {
        OrderDetail order = failDelivery(BUYER_ID);
        fixSeller(order);
        Long recoveryId = recoveryOf(order).getId();

        Throwable retryError = whileRecordLocked(recoveryId,
            recovery -> {
                orderService.cancelForPaymentFailure(order.orderId());
                recovery.cancel(LocalDateTime.now());
            },
            () -> recoveryRetry.retry(recoveryId, ADMIN_ID));

        assertThat(retryError).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.RECOVERY_CLOSED));
        assertThat(orderRepository.findById(order.orderId()).orElseThrow().getStatus())
            .isEqualTo(Order.Status.CANCELLED);
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).isEmpty();
        assertThat(cancelledEventsOf(order.orderId())).isEqualTo(1);
    }

    @Test
    @DisplayName("재처리 · 취소가 기록을 잠그고 있으면 실패 보관은 기다리지 않고 바로 실패하고(리스너는 원래 예외 + suppressed), 기록은 그대로")
    void recordFailureDoesNotWaitForLockedRecord() throws Exception {
        OrderDetail order = failDelivery(BUYER_ID);
        Long recoveryId = recoveryOf(order).getId();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
                recoveryRepository.findByIdForUpdate(recoveryId).orElseThrow();
                locked.countDown();
                awaitQuietly(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).as("A가 기록을 잠금").isTrue();

            Future<?> direct = executor.submit(() -> recoveryService.recordFailure(new MarkPaidCommand(
                order.orderId(), PAYMENT_ID, BUYER_ID, order.totalAmount(), List.of()),
                new IllegalStateException("x")));
            assertThatThrownBy(() -> direct.get(2, TimeUnit.SECONDS)).as("기다리지 않고 바로 실패")
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PessimisticLockingFailureException.class);
            Future<?> listener = executor.submit(() -> eventDelivery.invoke(completed(order)));
            assertThatThrownBy(() -> listener.get(2, TimeUnit.SECONDS)).as("리스너도 멈추지 않음")
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
        PaymentRecovery recovery = recoveryOf(order);
        assertThat(recovery.getAttemptCount()).isEqualTo(1);
        assertThat(recovery.getStatus()).isEqualTo(PaymentRecovery.Status.PENDING);
    }

    /**
     * 스레드 A가 트랜잭션 안에서 기록을 행 잠금으로 읽어 쥔 채, 스레드 B가 call을 시작한다. B가 1초 안에 끝나지 않는지(잠금 대기)
     * 확인한 뒤 A가 close로 기록을 바꾸고 커밋하면, B가 끝나며 던진 예외를 돌려준다(끝까지 성공하면 null).
     * 모든 대기에 제한 시간을 두고 finally에서 잠금을 풀고 스레드를 정리한다.
     */
    private Throwable whileRecordLocked(Long recoveryId, Consumer<PaymentRecovery> close,
        Callable<?> call) throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
                PaymentRecovery recovery = recoveryRepository.findByIdForUpdate(recoveryId).orElseThrow();
                locked.countDown();
                awaitQuietly(release);
                close.accept(recovery);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).as("A가 기록을 잠금").isTrue();
            Future<?> waiter = executor.submit(call);

            assertThatThrownBy(() -> waiter.get(1, TimeUnit.SECONDS)).as("B는 잠금이 풀릴 때까지 기다림")
                .isInstanceOf(TimeoutException.class);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            try {
                waiter.get(10, TimeUnit.SECONDS);
                return null;
            } catch (ExecutionException e) {
                return e.getCause();
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
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

    /**
     * 판매자 B 주문상품의 seller_id를 NULL로 바꾼 뒤 맞는 결제 완료를 보내 배송 생성 실패로 보관한다.
     */
    private OrderDetail failDelivery(Long buyerId) {
        OrderDetail order = placeWithCart(buyerId);
        breakSeller(order);
        assertThatThrownBy(() -> eventDelivery.invoke(completed(order, buyerId)))
            .isInstanceOf(DataIntegrityViolationException.class);
        return order;
    }

    /**
     * 동시성 실패를 CI 로그만으로 볼 수 있게: 그 회차의 주문 · 기록 상태 · order.cancelled 수와
     * 허용 목록 밖 예외마다 클래스 · 메시지 · 원인 사슬 · suppressed를 남긴다.
     */
    private String diagnose(int round, OrderDetail order, ConcurrentCalls.Outcome outcome) {
        StringBuilder text = new StringBuilder(round + "회차 허용 목록 밖 예외")
            .append(" orderStatus=").append(orderRepository.findById(order.orderId())
                .map(stored -> stored.getStatus().name()).orElse("none"))
            .append(" recoveryStatus=").append(recoveryRepository.findByOrderId(order.orderId())
                .map(stored -> stored.getStatus() + " attemptCount=" + stored.getAttemptCount()).orElse("none"))
            .append(" orderCancelled=").append(cancelledEventsOf(order.orderId()))
            .append(" deliveries=").append(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId()).size())
            .append(" conflicts=").append(outcome.conflicts());
        for (Throwable error : outcome.unexpected()) {
            text.append("\n- ");
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                text.append(cause.getClass().getName()).append(": ").append(cause.getMessage()).append("\n    <- ");
            }
            for (Throwable suppressed : error.getSuppressed()) {
                text.append("\n    suppressed ").append(suppressed);
            }
        }
        return text.toString();
    }

    /**
     * 재처리 · 취소 경합에서 진 쪽의 정상 결과: 낙관적 락 · 배송 유니크(global 409), 또는 이긴 쪽이 기록을 닫았거나
     * 주문을 결제완료로 바꿔 RECOVERY_CLOSED · RECOVERY_ORDER_PAID(409). 그 밖의 업무 오류는 정상이 아니다.
     */
    private static boolean isLostOrClosed(Throwable error) {
        if (PaymentFailures.isLostConcurrentDuplicate(error)) {
            return true;
        }
        return error instanceof BusinessException business
            && (business.getErrorCode() == OrderErrorCode.RECOVERY_CLOSED
                || business.getErrorCode() == OrderErrorCode.RECOVERY_ORDER_PAID);
    }

    private OrderDetail placeWithCart(Long buyerId) {
        cartService.addItem(buyerId, 1L, null, 1);
        cartService.addItem(buyerId, 2L, null, 1);
        return orderService.place(buyerId, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L), new PlaceOrderCommand.Item(2L, null, 1, 30_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private void breakSeller(OrderDetail order) {
        jdbcTemplate.update("update orders.order_item set seller_id = null where order_id = ? and seller_id = ?",
            order.orderId(), OTHER_SELLER_ID);
    }

    private void fixSeller(OrderDetail order) {
        jdbcTemplate.update("update orders.order_item set seller_id = ? where order_id = ? and seller_id is null",
            OTHER_SELLER_ID, order.orderId());
    }

    private long historyCount(Long orderId, String targetType, String toStatus) {
        return jdbcTemplate.queryForObject("select count(*) from orders.order_status_history "
            + "where order_id = ? and target_type = ? and to_status = ?", Long.class, orderId, targetType, toStatus);
    }

    private PaymentRecovery recoveryOf(OrderDetail order) {
        return recoveryRepository.findByOrderId(order.orderId()).orElseThrow();
    }

    // 주문상품별 금액은 지연 로딩이라 트랜잭션 안에서 읽는 조회 결과로 본다
    private List<PaymentRecoveryResult.Item> itemsOf(PaymentRecovery recovery) {
        return recoveryService.getRecovery(recovery.getId()).items();
    }

    private Long buyerOf(OrderDetail order) {
        return orderRepository.findById(order.orderId()).orElseThrow().getMemberId();
    }

    private long cancelledEventsOf(Long orderId) {
        return recorder.events(OrderCancelledEvent.class).stream()
            .filter(event -> event.data().orderId().equals(orderId))
            .count();
    }

    private static PaymentCompletedEvent completed(OrderDetail order) {
        return completed(order, BUYER_ID);
    }

    private static PaymentCompletedEvent completed(OrderDetail order, Long buyerId) {
        LocalDateTime now = LocalDateTime.now();
        return new PaymentCompletedEvent(UUID.randomUUID(), now, new PaymentCompletedEvent.Data(
            PAYMENT_ID, order.orderId(), buyerId, order.totalAmount(), itemsOf(order), now));
    }

    private static List<PaymentCompletedEvent.Item> itemsOf(OrderDetail order) {
        return order.items().stream()
            .map(item -> new PaymentCompletedEvent.Item(item.orderItemId(), item.amount()))
            .toList();
    }
}
