package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.ORDER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentOutboxRecorder;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventOutbox;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxRecorderTest {

    @Mock
    private PaymentEventOutbox paymentEventOutbox;

    @InjectMocks
    private PaymentOutboxRecorder paymentOutboxRecorder;

    @Test
    @DisplayName("결제가 발행한 이벤트는 아웃박스에 저장한다")
    void recordPaymentEvent() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(UUID.randomUUID(), NOW,
            new PaymentCompletedEvent.Data(1L, ORDER_ID, MEMBER_ID, 10_000L,
                List.of(new PaymentCompletedEvent.Item(10L, 10_000L)), NOW));

        paymentOutboxRecorder.record(event);

        then(paymentEventOutbox).should().append(event);
    }

    @Test
    @DisplayName("다른 도메인이 발행한 이벤트는 저장하지 않는다")
    void skipOtherProducer() {
        OrderPaymentRequestedEvent event = new OrderPaymentRequestedEvent(UUID.randomUUID(), NOW,
            new OrderPaymentRequestedEvent.Data(ORDER_ID, MEMBER_ID, 10_000L,
                List.of(new OrderPaymentRequestedEvent.Item(10L, 10_000L))));

        paymentOutboxRecorder.record(event);

        then(paymentEventOutbox).should(never()).append(any());
    }
}
