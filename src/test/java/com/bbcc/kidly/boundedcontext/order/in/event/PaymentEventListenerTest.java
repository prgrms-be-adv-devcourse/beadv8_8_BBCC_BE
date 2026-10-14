package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.delivery.DeliveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
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

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    private static final Long ORDER_ID = 7L;
    private static final Long PAYMENT_ID = 500L;

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
        @DisplayName("주문 ID · 결제 ID로 결제완료를 맡기고, 바뀌었으면 판매자별 주문을 만든 뒤 장바구니를 정리한다")
        void marksPaidThenCreatesDeliveriesAndClearsCart() {
            when(orderService.markPaid(ORDER_ID, PAYMENT_ID)).thenReturn(true);

            listener.onPaymentCompleted(event());

            verify(orderService).markPaid(ORDER_ID, PAYMENT_ID);
            verify(deliveryService).createFor(ORDER_ID);
            verify(cartService).clearOrdered(ORDER_ID);
        }

        @Test
        @DisplayName("이미 결제완료 · 취소된 주문이라 바뀌지 않았으면 아무것도 더 하지 않는다 (중복 이벤트)")
        void doesNothingWhenNotChanged() {
            listener.onPaymentCompleted(event());

            verify(orderService).markPaid(ORDER_ID, PAYMENT_ID);
            verify(deliveryService, never()).createFor(anyLong());
            verify(cartService, never()).clearOrdered(anyLong());
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
