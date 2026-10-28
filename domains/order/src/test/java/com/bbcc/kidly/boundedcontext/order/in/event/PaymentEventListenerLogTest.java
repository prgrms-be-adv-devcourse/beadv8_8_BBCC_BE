package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.delivery.DeliveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderEventFailureService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 결제 완료 처리 중 예외의 로그 분류: 동시 중복 처리에서 진 쪽은 WARN, 그 밖은 ERROR. 어느 쪽이든 예외는 그대로 다시 던진다.
 * 커밋 단계 되돌림은 트랜잭션 완료 콜백으로 WARN(본문에서 이미 남겼으면 생략).
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
    private static final String ROLLED_BACK_MESSAGE = "커밋 단계에서 되돌려짐";
    private static final String ROLLED_BACK_REASON =
        "event=payment.completed reason=rolled-back-at-commit orderId=" + ORDER_ID + " paymentId=" + PAYMENT_ID;

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

    private PaymentEventListener listener;

    // 결제 완료 반영 순서는 실제 PaymentCompletion으로 두고, 그 안의 서비스만 mock으로 둔다
    @BeforeEach
    void setUp() {
        listener = new PaymentEventListener(orderService,
            new PaymentCompletion(orderService, deliveryService, cartService), recoveryService, failureService);
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
    @DisplayName("본문은 끝났는데 커밋 단계에서 되돌려지면 WARN을 한 번 남긴다(커밋되면 남기지 않음)")
    void rolledBackAtCommitIsWarnOnce(CapturedOutput output) {
        when(orderService.markPaid(COMMAND)).thenReturn(true);

        List<TransactionSynchronization> rolledBack = runInSynchronization(() -> listener.onPaymentCompleted(event()));
        rolledBack.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        List<TransactionSynchronization> committed = runInSynchronization(() -> listener.onPaymentCompleted(event()));
        committed.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        assertThat(output.getOut()).containsOnlyOnce(ROLLED_BACK_REASON).doesNotContain("ERROR");
        assertThat(output).containsPattern("WARN.*" + ROLLED_BACK_MESSAGE + ".*" + ROLLED_BACK_REASON);
    }

    @Test
    @DisplayName("본문에서 이미 분류 · 로그한 예외로 되돌려지면 커밋 단계 WARN은 남기지 않는다(중복 금지)")
    void noDuplicateWhenBodyAlreadyLogged(CapturedOutput output) {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_delivery_order_seller\"");
        when(orderService.markPaid(COMMAND)).thenReturn(true);
        doThrow(error).when(deliveryService).createFor(ORDER_ID);

        List<TransactionSynchronization> synchronizations = runInSynchronization(() ->
            assertThatThrownBy(() -> listener.onPaymentCompleted(event())).isSameAs(error));
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(output.getOut()).containsOnlyOnce(LOST_REASON).doesNotContain("reason=rolled-back-at-commit");
    }

    /**
     * 트랜잭션 동기화를 켠 채로 실행하고, 그동안 등록된 완료 콜백을 돌려준다(완료 신호는 테스트가 직접 보낸다).
     */
    private static List<TransactionSynchronization> runInSynchronization(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
            return TransactionSynchronizationManager.getSynchronizations();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
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
