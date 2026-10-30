package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SettlementOutboxPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfig.class);

    // 값은 ;로 나눈 설정 줄이다
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
        "kidly.settlement.outbox.batch-size=100;kidly.settlement.outbox.retention=P7D",
        "kidly.settlement.outbox.relay-interval=PT1S;kidly.settlement.outbox.retention=P7D",
        "kidly.settlement.outbox.relay-interval=PT1S;kidly.settlement.outbox.batch-size=100",
        "kidly.settlement.outbox.relay-interval=PT1S;kidly.settlement.outbox.batch-size=0;"
            + "kidly.settlement.outbox.retention=P7D",
        "kidly.settlement.outbox.relay-interval=PT0.5S;kidly.settlement.outbox.batch-size=100;"
            + "kidly.settlement.outbox.retention=P7D",
        "kidly.settlement.outbox.relay-interval=PT1S;kidly.settlement.outbox.batch-size=100;"
            + "kidly.settlement.outbox.retention=PT0S"})
    @DisplayName("값이 없거나, 건수가 0이거나, 주기 · 보관 기간이 1초 미만이면 기동에 실패한다")
    void failsOnMissingOrInvalidValue(String properties) {
        runner.withPropertyValues(properties.split(";"))
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("설정이 맞으면 그 값으로 만든다")
    void bindsValidValues() {
        runner.withPropertyValues("kidly.settlement.outbox.relay-interval=PT1S",
                "kidly.settlement.outbox.batch-size=100", "kidly.settlement.outbox.retention=P7D")
            .run(context -> assertThat(context.getBean(SettlementOutboxProperties.class))
                .isEqualTo(new SettlementOutboxProperties(Duration.ofSeconds(1), 100, Duration.ofDays(7))));
    }

    @EnableConfigurationProperties(SettlementOutboxProperties.class)
    static class PropertiesConfig {
    }
}
