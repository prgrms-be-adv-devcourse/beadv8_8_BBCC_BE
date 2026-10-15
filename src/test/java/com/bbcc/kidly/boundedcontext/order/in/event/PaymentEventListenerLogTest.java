package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.delivery.DeliveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
    private static final String LOST_MESSAGE = "결제 완료 처리 실패(동시 중복 처리에서 진 쪽)";
    private static final String FAILED_MESSAGE = "결제 완료 처리 실패: event=payment.completed";
    private static final String ROLLED_BACK_MESSAGE = "커밋 단계에서 되돌려짐";

    @Mock
    private OrderService orderService;

    @Mock
    private DeliveryService deliveryService;

    @Mock
    private CartService cartService;

    @InjectMocks
    private PaymentEventListener listener;

    @Test
    @DisplayName("배송 (order_id, seller_id) 유니크 위반이면 WARN을 남기고 같은 예외를 다시 던진다")
    void deliveryUniqueViolationIsWarn(CapturedOutput output) {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_delivery_order_seller\"");
        when(orderService.markPaid(COMMAND)).thenReturn(true);
        doThrow(error).when(deliveryService).createFor(ORDER_ID);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event())).isSameAs(error);

        assertThat(output).contains("WARN").contains(LOST_MESSAGE).doesNotContain("ERROR");
    }

    @Test
    @DisplayName("낙관적 락 충돌이면 WARN을 남기고 같은 예외를 다시 던진다")
    void optimisticLockIsWarn(CapturedOutput output) {
        ObjectOptimisticLockingFailureException error = new ObjectOptimisticLockingFailureException("Order", ORDER_ID);
        when(orderService.markPaid(COMMAND)).thenThrow(error);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event())).isSameAs(error);

        assertThat(output).contains("WARN").contains(LOST_MESSAGE).doesNotContain("ERROR");
    }

    @Test
    @DisplayName("다른 제약 위반은 진 쪽이 아니므로 ERROR를 남기고 같은 예외를 다시 던진다")
    void otherConstraintViolationIsError(CapturedOutput output) {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_cart_item\"");
        when(orderService.markPaid(COMMAND)).thenReturn(true);
        doThrow(error).when(cartService).clearOrdered(ORDER_ID);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event())).isSameAs(error);

        assertThat(output).contains("ERROR").contains(FAILED_MESSAGE).doesNotContain(LOST_MESSAGE);
    }

    @Test
    @DisplayName("그 밖의 예외는 ERROR를 남기고 같은 예외를 다시 던진다")
    void otherErrorIsError(CapturedOutput output) {
        IllegalStateException error = new IllegalStateException("배송 생성 실패");
        when(orderService.markPaid(COMMAND)).thenReturn(true);
        doThrow(error).when(deliveryService).createFor(ORDER_ID);

        assertThatThrownBy(() -> listener.onPaymentCompleted(event())).isSameAs(error);

        assertThat(output).contains("ERROR").contains(FAILED_MESSAGE).doesNotContain(LOST_MESSAGE);
    }

    @Test
    @DisplayName("본문은 끝났는데 커밋 단계에서 되돌려지면 WARN을 한 번 남긴다(커밋되면 남기지 않음)")
    void rolledBackAtCommitIsWarnOnce(CapturedOutput output) {
        when(orderService.markPaid(COMMAND)).thenReturn(true);

        List<TransactionSynchronization> rolledBack = runInSynchronization(() -> listener.onPaymentCompleted(event()));
        rolledBack.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        List<TransactionSynchronization> committed = runInSynchronization(() -> listener.onPaymentCompleted(event()));
        committed.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        assertThat(output.getOut()).containsOnlyOnce(ROLLED_BACK_MESSAGE).doesNotContain("ERROR");
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

        assertThat(output.getOut()).containsOnlyOnce(LOST_MESSAGE).doesNotContain(ROLLED_BACK_MESSAGE);
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

    private PaymentCompletedEvent event() {
        LocalDateTime now = LocalDateTime.now();
        return new PaymentCompletedEvent(UUID.randomUUID(), now, new PaymentCompletedEvent.Data(
            PAYMENT_ID, ORDER_ID, 1L, AMOUNT, List.of(), now));
    }
}
