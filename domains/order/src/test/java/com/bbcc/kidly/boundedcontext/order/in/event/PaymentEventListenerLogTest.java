package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.PaymentCompletionService;
import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.delivery.DeliveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderEventFailureService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.LostPaymentRecheck;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentRecoveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PaymentRecoveryResult;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * 결제 완료 처리 중 예외의 로그 분류: 동시 중복 처리에서 진 쪽은 WARN, 그 밖은 ERROR. 어느 쪽이든 예외는 그대로 다시 던진다.
 * 반영은 커밋까지 끝낸 뒤 돌아오므로 커밋 단계 실패도 같은 기준으로 나눈다(#532, 커밋 단계 전용 경고 없음).
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PaymentEventListenerLogTest {

    private static final Long ORDER_ID = 7L;
    private static final Long PAYMENT_ID = 500L;
    private static final long AMOUNT = 50_000L;
    private static final MarkPaidCommand COMMAND = new MarkPaidCommand(ORDER_ID, PAYMENT_ID, 1L, AMOUNT, List.of());
    private static final String LOST_REASON =
        "event=payment.completed reason=lost-concurrent-duplicate orderId=" + ORDER_ID + " paymentId=" + PAYMENT_ID;
    private static final String FAILED_REASON =
        "event=payment.completed reason=processing-failed orderId=" + ORDER_ID + " paymentId=" + PAYMENT_ID;

    @Mock
    private OrderService orderService;

    @Mock
    private DeliveryService deliveryService;

    @Mock
    private CartService cartService;

    @Mock
    private PaymentRecoveryService recoveryService;

    @Mock
    private OrderEventFailureService failureService;

    @Mock
    private PaymentCompletionService completion;

    private PaymentEventListener listener;

    // 결제 완료 반영 순서는 실제 PaymentCompletionService로 두고, 그 안의 서비스만 mock으로 둔다
    @BeforeEach
    void setUp() {
        listener = new PaymentEventListener(orderService,
            new PaymentCompletionService(orderService, deliveryService, cartService), recoveryService, failureService);
    }

    @Test
    @DisplayName("배송 (order_id, seller_id) 유니크 위반이면 WARN을 남기고 같은 예외를 다시 던진다(보관하지 않음)")
    void deliveryUniqueViolationIsWarn(CapturedOutput output) {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_delivery_order_seller\"");
        when(orderService.markPaid(COMMAND)).thenReturn(true);
        doThrow(error).when(deliveryService).createFor(ORDER_ID);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event())).isSameAs(error);

        assertThat(output).containsPattern("WARN.*" + LOST_REASON).doesNotContain("ERROR");
        verify(recoveryService, never()).recordFailure(any(), any());
    }

    @Test
    @DisplayName("낙관적 락 충돌이면 WARN을 남기고 같은 예외를 다시 던진다(보관하지 않음)")
    void optimisticLockIsWarn(CapturedOutput output) {
        ObjectOptimisticLockingFailureException error = new ObjectOptimisticLockingFailureException("Order", ORDER_ID);
        when(orderService.markPaid(COMMAND)).thenThrow(error);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event())).isSameAs(error);

        assertThat(output).containsPattern("WARN.*" + LOST_REASON).doesNotContain("ERROR");
        verify(recoveryService, never()).recordFailure(any(), any());
    }

    @Test
    @DisplayName("진 쪽인데 이긴 쪽이 주문을 취소했으면 환불 확인 WARN(서비스)만 남기고 진 쪽 WARN은 남기지 않은 채 같은 예외를 다시 던진다 (#519)")
    void lostToCancelLeavesRefundCheckOnly(CapturedOutput output) {
        ObjectOptimisticLockingFailureException error = new ObjectOptimisticLockingFailureException("Order", ORDER_ID);
        when(orderService.markPaid(COMMAND)).thenThrow(error);
        when(orderService.recheckLostPaymentCompleted(COMMAND)).thenReturn(LostPaymentRecheck.PAID_AFTER_CANCEL);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event()))
            .isSameAs(error)
            .satisfies(thrown -> assertThat(EventFailureKeeper.isHandled(thrown)).as("결과가 이미 남 = 처리 완료").isTrue());

        assertThat(output).doesNotContain("lost-concurrent-duplicate").doesNotContain("ERROR");
        verify(recoveryService, never()).recordFailure(any(), any());
    }

    @Test
    @DisplayName("진 쪽인데 다시 읽은 주문이 결제완료 등 결과가 났으면 진 쪽 WARN을 남기고 처리 완료 표시를 붙여 다시 던진다 (#519 · #594)")
    void lostToOtherKeepsLostWarn(CapturedOutput output) {
        ObjectOptimisticLockingFailureException error = new ObjectOptimisticLockingFailureException("Order", ORDER_ID);
        when(orderService.markPaid(COMMAND)).thenThrow(error);
        when(orderService.recheckLostPaymentCompleted(COMMAND)).thenReturn(LostPaymentRecheck.SETTLED);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event()))
            .isSameAs(error)
            .satisfies(thrown -> assertThat(EventFailureKeeper.isHandled(thrown)).isTrue());

        assertThat(output).containsPattern("WARN.*" + LOST_REASON).doesNotContain("ERROR");
        verify(orderService).recheckLostPaymentCompleted(COMMAND);
    }

    @Test
    @DisplayName("진 쪽인데 다시 읽은 주문이 아직 결제대기면(반영이 통째로 되돌려짐) 처리 완료 표시 없이 다시 던진다(Kafka가 다시 처리, #594)")
    void lostButStillPendingIsNotMarked(CapturedOutput output) {
        ObjectOptimisticLockingFailureException error = new ObjectOptimisticLockingFailureException("CartItem", 9L);
        when(orderService.markPaid(COMMAND)).thenThrow(error);
        when(orderService.recheckLostPaymentCompleted(COMMAND)).thenReturn(LostPaymentRecheck.STILL_PENDING);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event()))
            .isSameAs(error)
            .satisfies(thrown -> assertThat(EventFailureKeeper.isHandled(thrown)).isFalse());

        assertThat(output).containsPattern("WARN.*" + LOST_REASON).doesNotContain("ERROR");
    }

    @Test
    @DisplayName("진 쪽 주문을 다시 읽다 실패해도 원래 예외를 그대로 던지고(읽기 오류는 suppressed) 진 쪽 WARN을 남긴다 (#519)")
    void rereadFailureKeepsOriginal(CapturedOutput output) {
        ObjectOptimisticLockingFailureException error = new ObjectOptimisticLockingFailureException("Order", ORDER_ID);
        IllegalStateException readError = new IllegalStateException("주문 다시 읽기 실패");
        when(orderService.markPaid(COMMAND)).thenThrow(error);
        when(orderService.recheckLostPaymentCompleted(COMMAND)).thenThrow(readError);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event()))
            .isSameAs(error)
            .satisfies(thrown -> assertThat(thrown.getSuppressed()).as("모르면 처리 완료 표시 없음 = 다시 처리")
                .containsExactly(readError));

        assertThat(output).containsPattern("WARN.*" + LOST_REASON).doesNotContain("ERROR");
    }

    @Test
    @DisplayName("다른 제약 위반은 진 쪽이 아니므로 보관하고, 보관 결과를 붙인 ERROR 한 줄을 남긴 뒤 같은 예외를 다시 던진다")
    void otherConstraintViolationIsError(CapturedOutput output) {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_cart_item\"");
        when(orderService.markPaid(COMMAND)).thenReturn(true);
        doThrow(error).when(cartService).clearOrdered(ORDER_ID);
        when(recoveryService.recordFailure(COMMAND, error)).thenReturn(saved("DataIntegrityViolationException"));

        assertThatThrownBy(() -> listener.onPaymentCompleted(event())).isSameAs(error);

        assertThat(output).containsPattern("ERROR.*" + FAILED_REASON
                + " recoveryStatus=PENDING attemptCount=1 failureCode=DataIntegrityViolationException")
            .doesNotContain("lost-concurrent-duplicate");
        assertThat(output.getOut()).containsOnlyOnce("reason=processing-failed");
    }

    @Test
    @DisplayName("그 밖의 예외는 보관하고 ERROR 한 줄을 남긴 뒤 같은 예외를 다시 던진다")
    void otherErrorIsError(CapturedOutput output) {
        IllegalStateException error = new IllegalStateException("배송 생성 실패");
        when(orderService.markPaid(COMMAND)).thenReturn(true);
        doThrow(error).when(deliveryService).createFor(ORDER_ID);
        when(recoveryService.recordFailure(COMMAND, error)).thenReturn(saved("IllegalStateException"));

        assertThatThrownBy(() -> listener.onPaymentCompleted(event())).isSameAs(error);

        assertThat(output).containsPattern("ERROR.*" + FAILED_REASON
                + " recoveryStatus=PENDING attemptCount=1 failureCode=IllegalStateException")
            .doesNotContain("lost-concurrent-duplicate");
    }

    @Test
    @DisplayName("보관에 성공하면 Kafka 리스너가 처리 완료로 보게 보관 표시를 붙인다(#594). 같은 예외 그대로")
    void storedFailureIsMarkedKept() {
        IllegalStateException error = new IllegalStateException("배송 생성 실패");
        when(orderService.markPaid(COMMAND)).thenReturn(true);
        doThrow(error).when(deliveryService).createFor(ORDER_ID);
        when(recoveryService.recordFailure(COMMAND, error)).thenReturn(saved("IllegalStateException"));

        assertThatThrownBy(() -> listener.onPaymentCompleted(event()))
            .isSameAs(error)
            .satisfies(thrown -> assertThat(EventFailureKeeper.isHandled(thrown)).isTrue());
    }

    @Test
    @DisplayName("보관이 실패하면 보관 표시를 붙이지 않는다(Kafka 리스너가 다시 던져 재시도 → DLT)")
    void unstoredFailureIsNotMarkedKept() {
        IllegalStateException error = new IllegalStateException("배송 생성 실패");
        when(orderService.markPaid(COMMAND)).thenReturn(true);
        doThrow(error).when(deliveryService).createFor(ORDER_ID);
        when(recoveryService.recordFailure(COMMAND, error)).thenThrow(new IllegalStateException("보관 실패"));

        assertThatThrownBy(() -> listener.onPaymentCompleted(event()))
            .isSameAs(error)
            .satisfies(thrown -> assertThat(EventFailureKeeper.isHandled(thrown)).isFalse());
    }

    @Test
    @DisplayName("보관이 실패해도 원래 예외를 그대로 던지고(보관 오류는 suppressed), 로그에는 보관하지 못했다고 남긴다")
    void recordFailureErrorKeepsOriginal(CapturedOutput output) {
        IllegalStateException error = new IllegalStateException("배송 생성 실패");
        IllegalStateException recordError = new IllegalStateException("기록 저장 실패");
        when(orderService.markPaid(COMMAND)).thenReturn(true);
        doThrow(error).when(deliveryService).createFor(ORDER_ID);
        when(recoveryService.recordFailure(COMMAND, error)).thenThrow(recordError);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event()))
            .isSameAs(error)
            .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(recordError));

        assertThat(output).containsPattern("ERROR.*" + FAILED_REASON + " recoveryStatus=not-saved");
    }

    @Test
    @DisplayName("재처리 · 취소가 기록을 잠그고 있어 보관이 바로 실패하면 기록 잠김으로 남기고 원래 예외를 그대로 던진다")
    void lockedRecordIsRecordLocked(CapturedOutput output) {
        IllegalStateException error = new IllegalStateException("배송 생성 실패");
        CannotAcquireLockException recordError = new CannotAcquireLockException("could not obtain lock on row");
        when(orderService.markPaid(COMMAND)).thenReturn(true);
        doThrow(error).when(deliveryService).createFor(ORDER_ID);
        when(recoveryService.recordFailure(COMMAND, error)).thenThrow(recordError);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event()))
            .isSameAs(error)
            .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(recordError));

        assertThat(output).containsPattern("ERROR.*" + FAILED_REASON + " recoveryStatus=record-locked")
            .doesNotContain("recoveryStatus=not-saved");
    }

    @Test
    @DisplayName("같은 주문의 결제 완료가 동시에 실패해 늦은 쪽 보관이 기록 유니크에 걸리면 이미 보관됨으로 남긴다(기록은 1건 있음)")
    void lateRecordIsAlreadyRecorded(CapturedOutput output) {
        IllegalStateException error = new IllegalStateException("배송 생성 실패");
        DataIntegrityViolationException recordError = new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_payment_recovery_order\"");
        when(orderService.markPaid(COMMAND)).thenReturn(true);
        doThrow(error).when(deliveryService).createFor(ORDER_ID);
        when(recoveryService.recordFailure(COMMAND, error)).thenThrow(recordError);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event()))
            .isSameAs(error)
            .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(recordError));

        assertThat(output).containsPattern("ERROR.*" + FAILED_REASON + " recoveryStatus=already-recorded")
            .doesNotContain("recoveryStatus=not-saved");
    }

    @Test
    @DisplayName("반영이 커밋 단계에서 진 쪽(낙관적 락)으로 실패해도 본문과 같이 진 쪽 WARN 한 줄만 남기고 같은 예외를 다시 던진다 (#532)")
    void lostAtCommitIsLostWarn(CapturedOutput output) {
        ObjectOptimisticLockingFailureException error = new ObjectOptimisticLockingFailureException("Order", ORDER_ID);
        when(completion.applyForEvent(COMMAND)).thenThrow(error);

        assertThatThrownBy(() -> listenerWith(completion).onPaymentCompleted(event())).isSameAs(error);

        assertThat(output.getOut()).containsOnlyOnce(LOST_REASON).doesNotContain("ERROR")
            .doesNotContain("rolled-back-at-commit");
        verify(recoveryService, never()).recordFailure(any(), any());
    }

    @Test
    @DisplayName("반영이 커밋 단계에서 그 밖의 이유로 실패하면 본문과 같이 보관하고 보관 결과를 붙인 ERROR 한 줄 뒤 같은 예외를 다시 던진다 (#532)")
    void otherFailureAtCommitIsKept(CapturedOutput output) {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_cart_item\"");
        when(completion.applyForEvent(COMMAND)).thenThrow(error);
        when(recoveryService.recordFailure(COMMAND, error)).thenReturn(saved("DataIntegrityViolationException"));

        assertThatThrownBy(() -> listenerWith(completion).onPaymentCompleted(event())).isSameAs(error);

        assertThat(output).containsPattern("ERROR.*" + FAILED_REASON
            + " recoveryStatus=PENDING attemptCount=1 failureCode=DataIntegrityViolationException");
        assertThat(output.getOut()).containsOnlyOnce("reason=processing-failed")
            .doesNotContain("rolled-back-at-commit");
    }

    @Test
    @DisplayName("재시도가 끝난 결제 완료(#632)는 같은 형식으로 보관하고 보관 결과를 붙인 ERROR 한 줄을 남긴 뒤 true(DLT로 보내지 않음)")
    void keptAfterRetries(CapturedOutput output) {
        ObjectOptimisticLockingFailureException error = new ObjectOptimisticLockingFailureException("CartItem", 9L);
        when(recoveryService.recordFailure(COMMAND, error))
            .thenReturn(saved("ObjectOptimisticLockingFailureException"));

        assertThat(listener.keepAfterRetries(event(), error)).isTrue();

        assertThat(output).containsPattern("ERROR.*event=payment.completed reason=retries-exhausted orderId="
            + ORDER_ID + " paymentId=" + PAYMENT_ID + " recoveryStatus=PENDING attemptCount=1 "
            + "failureCode=ObjectOptimisticLockingFailureException");
        assertThat(output.getOut()).containsOnlyOnce("reason=retries-exhausted");
    }

    @Test
    @DisplayName("재시도가 끝난 결제 완료의 보관이 실패하면 false(지금처럼 DLT, 로그는 DLT 한 줄), 보관 오류는 원래 예외에 붙인다(#632)")
    void notKeptAfterRetries(CapturedOutput output) {
        ObjectOptimisticLockingFailureException error = new ObjectOptimisticLockingFailureException("CartItem", 9L);
        CannotAcquireLockException recordError = new CannotAcquireLockException("기록 잠김");
        when(recoveryService.recordFailure(COMMAND, error)).thenThrow(recordError);

        assertThat(listener.keepAfterRetries(event(), error)).isFalse();

        assertThat(error.getSuppressed()).contains(recordError);
        assertThat(output.getOut()).doesNotContain("retries-exhausted");
    }

    // 반영 서비스를 통째로 mock으로 두어, 커밋까지 끝낸 반영이 예외로 돌아오는 경우를 만든다
    private PaymentEventListener listenerWith(PaymentCompletionService paymentCompletion) {
        return new PaymentEventListener(orderService, paymentCompletion, recoveryService, failureService);
    }

    private static PaymentRecoveryResult saved(String failureCode) {
        LocalDateTime now = LocalDateTime.now();
        return new PaymentRecoveryResult(1L, ORDER_ID, PAYMENT_ID, 1L, AMOUNT, List.of(), "PENDING", failureCode, 1,
            now, now);
    }

    private PaymentCompletedEvent event() {
        LocalDateTime now = LocalDateTime.now();
        return new PaymentCompletedEvent(UUID.randomUUID(), now, new PaymentCompletedEvent.Data(
            PAYMENT_ID, ORDER_ID, 1L, AMOUNT, List.of(), now));
    }
}
