package com.bbcc.kidly.boundedcontext.order.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.port.PaymentEventListenerState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * 주문의 결제 이벤트 Kafka 리스너가 실행 중인지(#561): 레지스트리에서 정해 둔 리스너 id로 찾는다.
 * 리스너가 없으면(#594 전) · 멈춰 있으면(스위치 꺼짐 · 장애로 멈춤) 실행 중이 아니다.
 */
class KafkaPaymentEventListenerStateTest {

    private final KafkaListenerEndpointRegistry registry = mock();
    private final KafkaPaymentEventListenerState state = new KafkaPaymentEventListenerState(registry);

    @Test
    @DisplayName("리스너 id는 주문 Kafka 리스너 이름 규칙(order + 받는 이벤트 + KafkaListener)을 따른다")
    void listenerId() {
        assertThat(PaymentEventListenerState.LISTENER_ID).isEqualTo("orderPaymentEventKafkaListener");
    }

    @Test
    @DisplayName("레지스트리에 그 id의 리스너가 없으면 실행 중이 아니다")
    void absent() {
        when(registry.getListenerContainer(PaymentEventListenerState.LISTENER_ID)).thenReturn(null);

        assertThat(state.isRunning()).isFalse();
    }

    @Test
    @DisplayName("리스너가 있어도 멈춰 있으면 실행 중이 아니다")
    void stopped() {
        MessageListenerContainer listener = mock();
        when(listener.isRunning()).thenReturn(false);
        when(registry.getListenerContainer(PaymentEventListenerState.LISTENER_ID)).thenReturn(listener);

        assertThat(state.isRunning()).isFalse();
    }

    @Test
    @DisplayName("리스너가 돌고 있으면 실행 중이다")
    void running() {
        MessageListenerContainer listener = mock();
        when(listener.isRunning()).thenReturn(true);
        when(registry.getListenerContainer(PaymentEventListenerState.LISTENER_ID)).thenReturn(listener);

        assertThat(state.isRunning()).isTrue();
    }
}
