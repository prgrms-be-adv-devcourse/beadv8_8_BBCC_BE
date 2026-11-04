package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxRelayService;
import com.bbcc.kidly.boundedcontext.product.in.outbox.ProductOutboxScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 상품 아웃박스 릴레이는 한 프로세스에서만 돌아야 한다(릴레이에 잠금이 없다).
 * 릴레이 스위치(kidly.product.outbox.relay.enabled)가 false면 스케줄러를 만들지 않는다. 지금은 어느 환경에서도 끄지 않는다(#791).
 */
class ProductOutboxRelaySwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(ProductOutboxScheduler.class)
        .withBean(ProductOutboxRelayService.class, () -> mock(ProductOutboxRelayService.class))
        .withBean(ProductOutboxCleanupService.class, () -> mock(ProductOutboxCleanupService.class));

    @ParameterizedTest
    @ValueSource(strings = {"kidly.product.outbox.relay.enabled=false", "kidly.kafka.producer.enabled=false"})
    @DisplayName("릴레이 스위치나 Kafka 보내기 스위치를 끄면 상품 아웃박스 스케줄러를 만들지 않는다")
    void notRegisteredWhenSwitchIsOff(String property) {
        runner.withPropertyValues(property)
            .run(context -> assertThat(context).doesNotHaveBean(ProductOutboxScheduler.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"kidly.product.outbox.relay.enabled=true", "kidly.product.other=true"})
    @DisplayName("릴레이 스위치가 켜져 있거나 지정되지 않으면 상품 아웃박스 스케줄러를 만든다")
    void registeredByDefault(String property) {
        runner.withPropertyValues(property)
            .run(context -> assertThat(context).hasSingleBean(ProductOutboxScheduler.class));
    }
}
