package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.delivery.DeliveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PaymentEventListenerTest {

    private static final Long ORDER_ID = 7L;
    private static final Long PAYMENT_ID = 500L;
    private static final MarkPaidCommand COMMAND = new MarkPaidCommand(ORDER_ID, PAYMENT_ID, 1L, 1L,
        List.of(new MarkPaidCommand.Item(70L, 1L)));

    @Mock
    private OrderService orderService;

    @Mock
    private DeliveryService deliveryService;

    @Mock
    private CartService cartService;

    @InjectMocks
    private PaymentEventListener listener;

    @Nested
    @DisplayName("결제 완료 이벤트")
    class OnPaymentCompleted {

        @Test
        @DisplayName("주문 ID · 결제 ID · 회원 · 금액 · 주문상품별 금액으로 결제완료를 맡기고, 바뀌었으면 판매자별 주문을 만든 뒤 장바구니를 정리한다")
        void marksPaidThenCreatesDeliveriesAndClearsCart() {
            when(orderService.markPaid(COMMAND)).thenReturn(true);

            listener.onPaymentCompleted(event());

            verify(orderService).markPaid(COMMAND);
            verify(deliveryService).createFor(ORDER_ID);
            verify(cartService).clearOrdered(ORDER_ID);
        }

        @Test
        @DisplayName("이미 결제완료 · 취소된 주문이라 바뀌지 않았으면 아무것도 더 하지 않는다 (중복 이벤트)")
        void doesNothingWhenNotChanged() {
            listener.onPaymentCompleted(event());

            verify(orderService).markPaid(COMMAND);
            verify(deliveryService, never()).createFor(anyLong());
            verify(cartService, never()).clearOrdered(anyLong());
        }

        @Test
        @DisplayName("주문상품 목록이 비어 있으면(null) NPE 없이 빈 목록으로 넘겨 주문이 구성 불일치로 판단하게 한다")
        void passesMissingItemsAsEmpty() {
            LocalDateTime at = LocalDateTime.of(2026, 10, 14, 9, 0);
            listener.onPaymentCompleted(new PaymentCompletedEvent(UUID.randomUUID(), at,
                new PaymentCompletedEvent.Data(PAYMENT_ID, ORDER_ID, 1L, 1L, null, at)));

            verify(orderService).markPaid(new MarkPaidCommand(ORDER_ID, PAYMENT_ID, 1L, 1L, List.of()));
        }

        @Test
        @DisplayName("결제 ID가 없으면 결제완료는 그대로 맡기고(돈은 빠졌으므로) ERROR 한 줄을 남긴다")
        void missingPaymentIdStillMarksPaidWithError(CapturedOutput output) {
            LocalDateTime at = LocalDateTime.of(2026, 10, 14, 9, 0);
            MarkPaidCommand withoutPaymentId = new MarkPaidCommand(ORDER_ID, null, 1L, 1L,
                List.of(new MarkPaidCommand.Item(70L, 1L)));
            when(orderService.markPaid(withoutPaymentId)).thenReturn(true);

            listener.onPaymentCompleted(new PaymentCompletedEvent(UUID.randomUUID(), at,
                new PaymentCompletedEvent.Data(null, ORDER_ID, 1L, 1L,
                    List.of(new PaymentCompletedEvent.Item(70L, 1L)), at)));

            verify(orderService).markPaid(withoutPaymentId);
            verify(deliveryService).createFor(ORDER_ID);
            assertThat(output).containsPattern(
                "ERROR.*event=payment.completed reason=missing-payment-id orderId=" + ORDER_ID);
        }

        @Test
        @DisplayName("결제 ID · 결제 시각이 있으면 missing-payment-id · missing-paid-at ERROR를 남기지 않는다")
        void noErrorWhenPaymentIdPresent(CapturedOutput output) {
            listener.onPaymentCompleted(event());

            assertThat(output).doesNotContain("missing-payment-id").doesNotContain("missing-paid-at");
        }

        @Test
        @DisplayName("결제 시각이 없으면 결제완료는 그대로 맡기고(결제 시각은 처리 시각을 씀) ERROR 한 줄을 남긴다")
        void missingPaidAtStillMarksPaidWithError(CapturedOutput output) {
            LocalDateTime at = LocalDateTime.of(2026, 10, 14, 9, 0);
            when(orderService.markPaid(COMMAND)).thenReturn(true);

            listener.onPaymentCompleted(new PaymentCompletedEvent(UUID.randomUUID(), at,
                new PaymentCompletedEvent.Data(PAYMENT_ID, ORDER_ID, 1L, 1L,
                    List.of(new PaymentCompletedEvent.Item(70L, 1L)), null)));

            verify(orderService).markPaid(COMMAND);
            verify(deliveryService).createFor(ORDER_ID);
            assertThat(errorLines(output, "reason=missing-paid-at orderId=" + ORDER_ID)).isEqualTo(1);
            assertThat(output).doesNotContain("missing-payment-id");
        }

        @Test
        @DisplayName("결제 ID · 결제 시각이 둘 다 없으면 결제완료는 그대로 맡기고 사유별로 ERROR 한 줄씩(두 줄)")
        void missingPaymentIdAndPaidAtLogsEach(CapturedOutput output) {
            LocalDateTime at = LocalDateTime.of(2026, 10, 14, 9, 0);

            listener.onPaymentCompleted(new PaymentCompletedEvent(UUID.randomUUID(), at,
                new PaymentCompletedEvent.Data(null, ORDER_ID, 1L, 1L,
                    List.of(new PaymentCompletedEvent.Item(70L, 1L)), null)));

            verify(orderService).markPaid(new MarkPaidCommand(ORDER_ID, null, 1L, 1L,
                List.of(new MarkPaidCommand.Item(70L, 1L))));
            assertThat(errorLines(output, "reason=missing-payment-id orderId=" + ORDER_ID)).isEqualTo(1);
            assertThat(errorLines(output, "reason=missing-paid-at orderId=" + ORDER_ID)).isEqualTo(1);
        }

        private long errorLines(CapturedOutput output, String text) {
            return output.getAll().lines()
                .filter(line -> line.contains("ERROR") && line.contains("event=payment.completed " + text))
                .count();
        }
    }

    @Nested
    @DisplayName("결제 실패 이벤트")
    class OnPaymentFailed {

        @Test
        @DisplayName("주문 ID로 결제 실패 취소를 맡긴다. 결제완료 처리는 하지 않는다")
        void cancelsForPaymentFailure() {
            listener.onPaymentFailed(new PaymentFailedEvent(UUID.randomUUID(), LocalDateTime.of(2026, 10, 15, 9, 0),
                new PaymentFailedEvent.Data(PAYMENT_ID, ORDER_ID, 1L, 50_000L, PaymentFailureCode.INSUFFICIENT_BALANCE,
                    LocalDateTime.of(2026, 10, 15, 9, 0))));

            verify(orderService).cancelForPaymentFailure(ORDER_ID);
            verify(orderService, never()).markPaid(any());
        }
    }

    @Nested
    @DisplayName("주문 ID가 없는 이벤트")
    class MissingOrderId {

        @Test
        @DisplayName("결제 완료의 주문 ID가 없으면 WARN을 남기고 아무것도 하지 않는다")
        void completedWithoutOrderId(CapturedOutput output) {
            LocalDateTime at = LocalDateTime.of(2026, 10, 14, 9, 0);
            listener.onPaymentCompleted(new PaymentCompletedEvent(UUID.randomUUID(), at,
                new PaymentCompletedEvent.Data(PAYMENT_ID, null, 1L, 1L, List.of(), at)));

            verifyNoInteractions(orderService, deliveryService, cartService);
            assertThat(output).containsPattern("WARN.*event=payment.completed.*reason=missing-order-id");
        }

        @Test
        @DisplayName("결제 완료의 데이터가 없으면 WARN을 남기고 아무것도 하지 않는다")
        void completedWithoutData(CapturedOutput output) {
            listener.onPaymentCompleted(
                new PaymentCompletedEvent(UUID.randomUUID(), LocalDateTime.of(2026, 10, 14, 9, 0), null));

            verifyNoInteractions(orderService, deliveryService, cartService);
            assertThat(output).containsPattern("WARN.*event=payment.completed.*reason=missing-order-id");
        }

        @Test
        @DisplayName("결제 실패의 주문 ID가 없으면 WARN을 남기고 취소를 맡기지 않는다")
        void failedWithoutOrderId(CapturedOutput output) {
            listener.onPaymentFailed(new PaymentFailedEvent(UUID.randomUUID(), LocalDateTime.of(2026, 10, 15, 9, 0),
                new PaymentFailedEvent.Data(PAYMENT_ID, null, 1L, 50_000L, PaymentFailureCode.INSUFFICIENT_BALANCE,
                    LocalDateTime.of(2026, 10, 15, 9, 0))));

            verifyNoInteractions(orderService);
            assertThat(output).containsPattern("WARN.*event=payment.failed.*reason=missing-order-id");
        }

        @Test
        @DisplayName("결제 실패의 데이터가 없으면 WARN을 남기고 취소를 맡기지 않는다")
        void failedWithoutData(CapturedOutput output) {
            listener.onPaymentFailed(new PaymentFailedEvent(UUID.randomUUID(), LocalDateTime.of(2026, 10, 15, 9, 0),
                null));

            verifyNoInteractions(orderService);
            assertThat(output).containsPattern("WARN.*event=payment.failed.*reason=missing-order-id");
        }
    }

    /**
     * 금액 · 주문상품 · 결제 시각은 쓰지 않는다(일부러 엉뚱한 값).
     */
    private static PaymentCompletedEvent event() {
        return new PaymentCompletedEvent(UUID.randomUUID(), LocalDateTime.of(2026, 10, 14, 9, 0),
            new PaymentCompletedEvent.Data(PAYMENT_ID, ORDER_ID, 1L, 1L,
                List.of(new PaymentCompletedEvent.Item(70L, 1L)), LocalDateTime.of(2000, 1, 1, 0, 0)));
    }
}
