package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementItemService;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementPayoutResultService;
import com.bbcc.kidly.boundedcontext.settlement.in.SettlementOrderEventListener;
import com.bbcc.kidly.boundedcontext.settlement.in.SettlementPaymentEventListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 같은 이벤트를 Spring · Kafka 양쪽에서 받지 않도록, 스위치가 꺼져 있을 때만 Spring 리스너가 뜬다.
 * Kafka 리스너는 스위치로 시작 여부를 정한다(SettlementKafkaListenerTest).
 */
class SettlementEventSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(SettlementItemService.class, () -> mock(SettlementItemService.class))
        .withBean(SettlementPayoutResultService.class, () -> mock(SettlementPayoutResultService.class))
        .withUserConfiguration(SettlementOrderEventListener.class, SettlementPaymentEventListener.class);

    @Test
    @DisplayName("스위치가 켜져 있으면(Kafka로 받음) Spring 리스너가 뜨지 않는다")
    void kafkaOn() {
        runner.withPropertyValues("kidly.settlement.kafka.consumer.enabled=true")
            .run(context -> assertThat(context)
                .doesNotHaveBean(SettlementOrderEventListener.class)
                .doesNotHaveBean(SettlementPaymentEventListener.class));
    }

    @Test
    @DisplayName("스위치를 끄면 Spring 리스너로 받는다(장애 때 되돌리기)")
    void kafkaOff() {
        runner.withPropertyValues("kidly.settlement.kafka.consumer.enabled=false")
            .run(context -> assertThat(context)
                .hasSingleBean(SettlementOrderEventListener.class)
                .hasSingleBean(SettlementPaymentEventListener.class));
    }

    @Test
    @DisplayName("스위치 값이 없으면 Kafka로 받는 것으로 본다(기본 true)")
    void defaultIsKafka() {
        runner.run(context -> assertThat(context)
            .doesNotHaveBean(SettlementOrderEventListener.class)
            .doesNotHaveBean(SettlementPaymentEventListener.class));
    }
}
