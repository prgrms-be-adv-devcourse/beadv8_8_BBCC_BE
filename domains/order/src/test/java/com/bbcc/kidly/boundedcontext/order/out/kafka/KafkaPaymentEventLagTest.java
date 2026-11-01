package com.bbcc.kidly.boundedcontext.order.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.PaymentEventLag;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주문(kidly.order)의 결제 이벤트(kidly.payment.events) 처리 대기량 계산(#561): 파티션마다 끝 오프셋 − 커밋 오프셋을 더한다.
 * 커밋이 없는데 읽을 메시지가 있으면 아직 배정 · 첫 처리 전(NOT_ASSIGNED)이고, 확인은 2초 안에 끝난다.
 */
class KafkaPaymentEventLagTest {

    @Test
    @DisplayName("모든 파티션의 커밋 위치가 끝이면 대기 0")
    void caughtUp() {
        assertThat(KafkaPaymentEventLag.evaluate(Map.of(0, 5L, 1, 0L, 2, 3L), Map.of(0, 5L, 1, 0L, 2, 3L)))
            .isEqualTo(PaymentEventLag.Check.caughtUp());
    }

    @Test
    @DisplayName("끝 − 커밋을 모든 파티션에서 더한 값이 대기량")
    void lagging() {
        assertThat(KafkaPaymentEventLag.evaluate(Map.of(0, 5L, 1, 7L, 2, 3L), Map.of(0, 4L, 1, 5L, 2, 3L)))
            .isEqualTo(PaymentEventLag.Check.lagging(3));
    }

    @Test
    @DisplayName("커밋이 없는 파티션에 읽을 메시지가 있으면 배정 · 첫 처리 전")
    void notAssigned() {
        assertThat(KafkaPaymentEventLag.evaluate(Map.of(0, 5L, 1, 2L), Map.of(0, 5L)))
            .isEqualTo(PaymentEventLag.Check.notAssigned());
    }

    @Test
    @DisplayName("커밋이 없어도 그 파티션이 비어 있으면(끝 0) 읽을 것이 없으므로 대기 0")
    void emptyPartitionWithoutCommit() {
        assertThat(KafkaPaymentEventLag.evaluate(Map.of(0, 0L, 1, 4L), Map.of(1, 4L)))
            .isEqualTo(PaymentEventLag.Check.caughtUp());
    }

    @Test
    @DisplayName("Kafka에 닿지 않으면 2초 남짓 안에 확인 실패로 돌려준다(스케줄러 스레드를 오래 붙잡지 않음)")
    void unreachableFailsWithinTimeout() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        var lag = new KafkaPaymentEventLag(properties);
        try {
            long started = System.nanoTime();
            PaymentEventLag.Check check = lag.check();
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            assertThat(check.status()).isEqualTo(PaymentEventLag.Check.Status.CHECK_FAILED);
            assertThat(check.reason()).isNotBlank();
            assertThat(took).isLessThan(Duration.ofMillis(3_000));
        } finally {
            lag.destroy();
        }
    }
}
