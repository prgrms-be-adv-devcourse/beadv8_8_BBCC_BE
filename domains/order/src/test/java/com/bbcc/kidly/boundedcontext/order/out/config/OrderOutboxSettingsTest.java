package com.bbcc.kidly.boundedcontext.order.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 주문 아웃박스 릴레이 · 정리 설정(#540)이 order.yml 기본값과 환경변수로 정해지고, 잘못된 값이면 키 이름이 나오는
 * 바인딩 오류로 기동에 실패하는지. 실제 OS 환경변수 대신 가짜 환경변수 출처를 쓴다.
 */
class OrderOutboxSettingsTest {

    @Test
    @DisplayName("환경변수가 없으면 order.yml 기본값: 릴레이 주기 PT1S · 배치 100 · 보관 P7D · 정리 매일 04:00")
    void usesDefaults() {
        appWithOrderYml(Map.of()).run(context -> {
            assertThat(context.getBean(OrderOutboxProperties.class))
                .isEqualTo(new OrderOutboxProperties(Duration.ofSeconds(1), 100, Duration.ofDays(7)));
            assertThat(context.getEnvironment().getProperty("kidly.order.outbox.cleanup-cron"))
                .isEqualTo("0 0 4 * * *");
        });
    }

    @Test
    @DisplayName("환경변수를 주면 그 값")
    void overridesByEnvironmentVariables() {
        appWithOrderYml(Map.of(
            "ORDER_OUTBOX_RELAY_INTERVAL", "PT5S",
            "ORDER_OUTBOX_BATCH_SIZE", "10",
            "ORDER_OUTBOX_RETENTION", "P1D",
            "ORDER_OUTBOX_CLEANUP_CRON", "-"))
            .run(context -> {
                assertThat(context.getBean(OrderOutboxProperties.class))
                    .isEqualTo(new OrderOutboxProperties(Duration.ofSeconds(5), 10, Duration.ofDays(1)));
                assertThat(context.getEnvironment().getProperty("kidly.order.outbox.cleanup-cron")).isEqualTo("-");
            });
    }

    @ParameterizedTest(name = "{0}={1} → {2}")
    @CsvSource({
        "ORDER_OUTBOX_RELAY_INTERVAL, PT0.999S, kidly.order.outbox.relay-interval",
        "ORDER_OUTBOX_RELAY_INTERVAL, '', relayInterval",
        "ORDER_OUTBOX_BATCH_SIZE, 0, batchSize",
        "ORDER_OUTBOX_BATCH_SIZE, '', batchSize",
        "ORDER_OUTBOX_RETENTION, PT0.999S, kidly.order.outbox.retention",
        "ORDER_OUTBOX_RETENTION, '', retention"
    })
    @DisplayName("주기 · 보관 기간이 1초 미만이거나 비었거나 배치가 1보다 작으면 키 이름이 나오는 바인딩 오류로 기동 실패")
    void invalidValueFails(String name, String value, String key) {
        appWithOrderYml(Map.of(name, value))
            .run(context -> assertThat(context).getFailure()
                .hasStackTraceContaining("kidly.order.outbox")
                .hasStackTraceContaining(key)
                .rootCause().isNotInstanceOf(NullPointerException.class));
    }

    @EnableConfigurationProperties(OrderOutboxProperties.class)
    static class OutboxPropertiesOnly {
    }

    /**
     * 실제 앱처럼 order.yml을 읽고 가짜 환경변수를 그 위에 둔 컨텍스트.
     */
    private static ApplicationContextRunner appWithOrderYml(Map<String, Object> environmentVariables) {
        return new ApplicationContextRunner()
            .withUserConfiguration(OutboxPropertiesOnly.class)
            .withInitializer(context -> {
                try {
                    MutablePropertySources sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables));
                    new YamlPropertySourceLoader().load("order.yml", new ClassPathResource("order.yml"))
                        .forEach(sources::addLast);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
    }
}
