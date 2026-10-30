package com.bbcc.kidly.boundedcontext.order.out.messaging;

import com.bbcc.kidly.boundedcontext.order.app.port.PaymentEventListenerState;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * 결제 이벤트 Kafka 리스너가 실행 중인지 Kafka 리스너 레지스트리에서 id로 찾아 본다(#561). 없으면 실행 중이 아니다.
 */
@Component
class KafkaPaymentEventListenerState implements PaymentEventListenerState {

    private final KafkaListenerEndpointRegistry registry;

    KafkaPaymentEventListenerState(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean isRunning() {
        MessageListenerContainer listener = registry.getListenerContainer(LISTENER_ID);
        return listener != null && listener.isRunning();
    }
}
