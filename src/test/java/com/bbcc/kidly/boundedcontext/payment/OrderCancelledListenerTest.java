package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.OrderCancellationRetryService;
import com.bbcc.kidly.boundedcontext.payment.app.OrderCancellationService;
import com.bbcc.kidly.boundedcontext.payment.app.OrderCancelledListener;
import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderCancelCommand;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderCancelledListenerTest {

    private static final OrderCancelCommand COMMAND = new OrderCancelCommand(ORDER_ID, MEMBER_ID, "PAYMENT_EXPIRED");

    @Mock
    private OrderCancellationService orderCancellationService;

    @Mock
    private OrderCancellationRetryService orderCancellationRetryService;

    @InjectMocks
    private OrderCancelledListener orderCancelledListener;

    @Test
    @DisplayName("취소 처리가 끝나면 다시 처리할 기록을 남기지 않는다")
    void handled() {
        orderCancelledListener.on(orderCancelled());

        then(orderCancellationService).should().handle(COMMAND);
        then(orderCancellationRetryService).should(never()).remember(any(), any());
    }

    @Test
    @DisplayName("취소 처리가 실패하면 예외를 삼키고 다시 처리할 취소로 남긴다")
    void rememberFailure() {
        IllegalStateException failure = new IllegalStateException("DB 오류");
        willThrow(failure).given(orderCancellationService).handle(COMMAND);

        orderCancelledListener.on(orderCancelled());

        then(orderCancellationRetryService).should().remember(COMMAND, failure);
    }

    private static OrderCancelledEvent orderCancelled() {
        return new OrderCancelledEvent(UUID.randomUUID(), LocalDateTime.now(),
            new OrderCancelledEvent.Data(ORDER_ID, MEMBER_ID, "PAYMENT_EXPIRED", List.of(
                new OrderCancelledEvent.Item(1000L, 1L, 1))));
    }
}
