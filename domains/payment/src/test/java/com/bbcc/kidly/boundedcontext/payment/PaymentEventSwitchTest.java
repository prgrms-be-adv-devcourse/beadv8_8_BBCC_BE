package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.payment.app.MemberRegisteredListener;
import com.bbcc.kidly.boundedcontext.payment.app.OrderCancelledListener;
import com.bbcc.kidly.boundedcontext.payment.app.OrderPaymentRequestedListener;
import com.bbcc.kidly.boundedcontext.payment.app.PaymentEventHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 같은 이벤트를 Spring · Kafka 양쪽에서 받지 않도록, 스위치가 꺼져 있을 때만 Spring 리스너가 뜬다(#593).
 * Kafka 리스너는 스위치로 시작 여부를 정한다(PaymentKafkaListenerTest).
 */
class PaymentEventSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(PaymentEventHandler.class, () -> mock(PaymentEventHandler.class))
        .withUserConfiguration(MemberRegisteredListener.class, OrderPaymentRequestedListener.class,
            OrderCancelledListener.class);

    @Test
    @DisplayName("스위치가 켜져 있으면(Kafka로 받음) Spring 리스너가 뜨지 않는다")
    void kafkaOn() {
        runner.withPropertyValues("kidly.payment.kafka.consumer.enabled=true")
            .run(context -> assertThat(context)
                .doesNotHaveBean(MemberRegisteredListener.class)
                .doesNotHaveBean(OrderPaymentRequestedListener.class)
                .doesNotHaveBean(OrderCancelledListener.class));
    }

    @Test
    @DisplayName("스위치를 끄면 Spring 리스너로 받는다(장애 때 되돌리기)")
    void kafkaOff() {
        runner.withPropertyValues("kidly.payment.kafka.consumer.enabled=false")
            .run(context -> assertThat(context)
                .hasSingleBean(MemberRegisteredListener.class)
                .hasSingleBean(OrderPaymentRequestedListener.class)
                .hasSingleBean(OrderCancelledListener.class));
    }

    @Test
    @DisplayName("스위치 값이 없으면 Kafka로 받는 것으로 본다(기본 true)")
    void defaultIsKafka() {
        runner.run(context -> assertThat(context).doesNotHaveBean(MemberRegisteredListener.class));
    }
}
