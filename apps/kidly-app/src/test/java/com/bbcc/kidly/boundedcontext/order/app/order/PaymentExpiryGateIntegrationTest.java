package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.PaymentEventListenerState;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 결제 마감 만료 실행 조건(#561)이 실제 앱 컨텍스트에서: 주문에는 아직 결제 이벤트 Kafka 리스너가 없으므로(#594 전)
 * 대기량을 확인하지 않고(Kafka에 붙지 않고) 바로 실행을 허락한다 — 지금 동작 그대로.
 */
@IntegrationTest
class PaymentExpiryGateIntegrationTest {

    @Autowired
    private PaymentExpiryGate paymentExpiryGate;

    @Autowired
    private PaymentEventListenerState paymentEventListenerState;

    @Test
    @DisplayName("결제 이벤트 Kafka 리스너가 없어 실행 중이 아니고, 만료는 대기량 확인 없이 바로 허락된다")
    void allowsWithoutCheckBeforePaymentListener() {
        assertThat(paymentEventListenerState.isRunning()).isFalse();

        long started = System.nanoTime();
        assertThat(paymentExpiryGate.allowsRun()).isTrue();

        // 대기량을 확인했다면 Kafka가 없는 이 컨텍스트에서 2초 제한까지 기다린다
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(500));
    }
}
