package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * order.payment-requested를 Spring 이벤트로 받는다. Kafka 수신 스위치(kidly.payment.kafka.consumer.enabled)를 끈 경우에만 뜬다(#593).
 * 켜져 있으면 Kafka 리스너(in.event)가 받으므로, 같은 이벤트를 양쪽에서 받지 않는다. 처리는 PaymentEventHandler에 있다.
 */
@Component
@ConditionalOnProperty(name = "kidly.payment.kafka.consumer.enabled", havingValue = "false")
@RequiredArgsConstructor
public class OrderPaymentRequestedListener {

    private final PaymentEventHandler handler;

    @DomainEventListener
    public void on(OrderPaymentRequestedEvent event) {
        handler.orderPaymentRequested(event);
    }
}
