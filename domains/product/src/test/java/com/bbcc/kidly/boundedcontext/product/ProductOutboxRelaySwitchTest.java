package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxRelayService;
import com.bbcc.kidly.boundedcontext.product.in.outbox.ProductOutboxScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 상품 아웃박스 스케줄러는 Kafka 보내기 스위치(kidly.kafka.producer.enabled)만 따른다.
 * 두 앱이 함께 돌던 동안 쓰던 릴레이 스위치(kidly.product.outbox.relay.enabled)는 지웠으므로, 그 값을 넣어도 스케줄러를 만든다(#791).
 */
class ProductOutboxRelaySwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(ProductOutboxScheduler.class)
        .withBean(ProductOutboxRelayService.class, () -> mock(ProductOutboxRelayService.class))
        .withBean(ProductOutboxCleanupService.class, () -> mock(ProductOutboxCleanupService.class));

    @Test
    @DisplayName("Kafka 보내기 스위치를 끄면 상품 아웃박스 스케줄러를 만들지 않는다")
    void notRegisteredWhenProducerIsOff() {
        runner.withPropertyValues("kidly.kafka.producer.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(ProductOutboxScheduler.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"kidly.kafka.producer.enabled=true", "kidly.product.outbox.relay.enabled=false",
        "kidly.product.other=true"})
    @DisplayName("Kafka 보내기 스위치가 켜져 있거나 지정되지 않으면 옛 릴레이 스위치 값과 상관없이 상품 아웃박스 스케줄러를 만든다")
    void registeredByDefault(String property) {
        runner.withPropertyValues(property)
            .run(context -> assertThat(context).hasSingleBean(ProductOutboxScheduler.class));
    }
}
