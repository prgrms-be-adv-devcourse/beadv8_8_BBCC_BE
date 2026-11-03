package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.in.SettlementOrderEventListener;
import com.bbcc.kidly.boundedcontext.settlement.in.SettlementPaymentEventListener;
import com.bbcc.kidly.boundedcontext.settlement.in.outbox.SettlementOutboxRelayScheduler;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Kafka가 필요 없는 통합 테스트는 Kafka 없이 돈다(회의 10-29 안건 3).
 * Kafka 컨테이너를 띄우지 않고, Kafka로 보내는 스케줄러는 만들지 않으며(kidly.kafka.producer.enabled),
 * 구매확정 · 지급 결과는 Kafka 대신 Spring 이벤트로 받는다(kidly.settlement.kafka.consumer.enabled).
 */
@IntegrationTest
class KafkaDisabledIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("일반 통합 테스트에서는 정산 아웃박스 릴레이 스케줄러를 만들지 않는다")
    void noSettlementOutboxRelayScheduler() {
        assertThat(context.getBeanNamesForType(SettlementOutboxRelayScheduler.class)).isEmpty();
    }

    @Test
    @DisplayName("일반 통합 테스트에서는 구매확정 · 지급 결과를 Spring 이벤트로 받는다")
    void receivesEventsBySpringListeners() {
        assertThat(context.getBeanNamesForType(SettlementOrderEventListener.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(SettlementPaymentEventListener.class)).hasSize(1);
    }
}
