package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 결제 마감 만료 실행 조건(#561 · #594)이 실제 앱 컨텍스트에서: 테스트 설정은 주문 Kafka 수신 스위치가 꺼져 있으므로(Spring 수신)
 * 대기량을 확인하지 않고(Kafka에 붙지 않고) 바로 실행을 허락한다 — 테스트의 만료 동작은 그대로다.
 * 스위치가 켜졌을 때 실제 대기량 확인을 거치는 것은 PaymentExpiryGateKafkaIntegrationTest가 본다.
 */
@IntegrationTest
class PaymentExpiryGateIntegrationTest {

    @Autowired
    private PaymentExpiryGate paymentExpiryGate;

    @Autowired
    private OrderKafkaConsumerProperties consumerProperties;

    @Test
    @DisplayName("스위치가 꺼져 있어 만료는 대기량 확인 없이 바로 허락된다")
    void allowsWithoutCheckWhenSwitchOff() {
        assertThat(consumerProperties.enabled()).isFalse();

        long started = System.nanoTime();
        assertThat(paymentExpiryGate.allowsRun()).isTrue();

        // 대기량을 확인했다면 Kafka가 없는 이 컨텍스트에서 2초 제한까지 기다린다
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(500));
    }
}
