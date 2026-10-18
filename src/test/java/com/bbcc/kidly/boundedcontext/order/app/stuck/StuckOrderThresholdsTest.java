package com.bbcc.kidly.boundedcontext.order.app.stuck;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 멈춘 주문 기준 시간(#445): order.yml 기본값 · 환경변수 · 검증(1초 이상, 아니면 키 이름이 나오는 기동 실패).
 * 실제 OS 환경변수 대신 가짜 환경변수 출처를 써서 PC마다 결과가 같다.
 */
class StuckOrderThresholdsTest {

    @Test
    @DisplayName("환경변수가 없으면 order.yml 기본값: 재고 PT5M · 만료 PT5M · 배송 생성 PT5M · 복구 PT1H · 발송 P3D · 자동 확정 PT1H")
    void usesDefaults() {
        appWithOrderYml(Map.of()).run(context -> assertThat(context.getBean(StuckOrderThresholds.class))
            .isEqualTo(new StuckOrderThresholds(Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofMinutes(5),
                Duration.ofHours(1), Duration.ofDays(3), Duration.ofHours(1))));
    }

    @Test
    @DisplayName("환경변수를 주면 그 값으로 바뀐다(ORDER_STUCK_SHIPPING_WAIT=PT2M)")
    void overridesByEnvironmentVariable() {
        appWithOrderYml(Map.of("ORDER_STUCK_SHIPPING_WAIT", "PT2M"))
            .run(context -> assertThat(context.getBean(StuckOrderThresholds.class).shippingWait())
                .isEqualTo(Duration.ofMinutes(2)));
    }

    @ParameterizedTest(name = "{0}={1} → 기동 실패, 메시지에 {2}")
    @CsvSource({
        "ORDER_STUCK_STOCK_WAIT, '', stockWait",
        "ORDER_STUCK_STOCK_WAIT, PT0.999S, kidly.order.stuck.stock-wait",
        "ORDER_STUCK_EXPIRY_GRACE, PT0S, kidly.order.stuck.expiry-grace",
        "ORDER_STUCK_DELIVERY_WAIT, -PT1S, kidly.order.stuck.delivery-wait",
        "ORDER_STUCK_RECOVERY_WAIT, PT0.999S, kidly.order.stuck.recovery-wait",
        "ORDER_STUCK_SHIPPING_WAIT, PT0S, kidly.order.stuck.shipping-wait",
        "ORDER_STUCK_AUTO_CONFIRM_GRACE, -PT1S, kidly.order.stuck.auto-confirm-grace",
        "ORDER_STUCK_AUTO_CONFIRM_GRACE, '', autoConfirmGrace"
    })
    @DisplayName("비었거나 1초 미만(음수 · 0 · 0.999초)이면 NPE가 아니라 키 이름이 나오는 바인딩 오류로 기동 실패")
    void invalidFails(String name, String value, String key) {
        appWithOrderYml(Map.of(name, value))
            .run(context -> assertThat(context).getFailure()
                .hasRootCauseInstanceOf(BindValidationException.class)
                .rootCause().hasMessageContaining(key));
    }

    @ParameterizedTest(name = "{0}=PT1S → 기동")
    @ValueSource(strings = {
        "ORDER_STUCK_STOCK_WAIT", "ORDER_STUCK_EXPIRY_GRACE", "ORDER_STUCK_DELIVERY_WAIT",
        "ORDER_STUCK_RECOVERY_WAIT", "ORDER_STUCK_SHIPPING_WAIT", "ORDER_STUCK_AUTO_CONFIRM_GRACE"})
    @DisplayName("정확히 1초(PT1S)면 기동한다")
    void exactlyOneSecondStarts(String name) {
        appWithOrderYml(Map.of(name, "PT1S")).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("값이 하나도 없으면(order.yml 없음) 기동 실패, 키 이름이 나온다")
    void missingFails() {
        new ApplicationContextRunner()
            .withUserConfiguration(ThresholdsOnly.class)
            .run(context -> assertThat(context).getFailure()
                .hasRootCauseInstanceOf(BindValidationException.class)
                .rootCause().hasMessageContaining("kidly.order.stuck").hasMessageContaining("stockWait"));
    }

    @EnableConfigurationProperties(StuckOrderThresholds.class)
    static class ThresholdsOnly {
    }

    private static ApplicationContextRunner appWithOrderYml(Map<String, Object> environmentVariables) {
        return new ApplicationContextRunner()
            .withUserConfiguration(ThresholdsOnly.class)
            .withInitializer(context -> {
                try {
                    MutablePropertySources sources = context.getEnvironment().getPropertySources();
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
