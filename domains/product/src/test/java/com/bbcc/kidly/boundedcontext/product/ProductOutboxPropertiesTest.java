package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductOutboxPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfig.class);

    // 값은 ;로 나눈 설정 줄이다. batch-size가 없으면 0으로 묶인다
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
        "kidly.product.outbox.retention=P7D",
        "kidly.product.outbox.batch-size=0;kidly.product.outbox.retention=P7D",
        "kidly.product.outbox.batch-size=100"})
    @DisplayName("한 번에 보낼 건수가 없거나 0이면, 또는 보관 기간이 없으면 기동에 실패한다")
    void failsOnMissingOrInvalidValue(String properties) {
        runner.withPropertyValues(properties.split(";"))
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("설정이 맞으면 그 값으로 만든다")
    void bindsValidValues() {
        runner.withPropertyValues("kidly.product.outbox.batch-size=100", "kidly.product.outbox.retention=P7D")
            .run(context -> assertThat(context.getBean(ProductOutboxProperties.class))
                .isEqualTo(new ProductOutboxProperties(100, Duration.ofDays(7))));
    }

    @EnableConfigurationProperties(ProductOutboxProperties.class)
    static class PropertiesConfig {
    }
}
