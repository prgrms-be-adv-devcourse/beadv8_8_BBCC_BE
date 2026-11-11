package com.bbcc.kidly.boundedcontext.wish.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 조르기 만료 설정(#1008): order.yml 기본값과 환경변수 덮어쓰기, 1초 미만 기동 실패. 주문 OrderSettingsTest와 같은 방식으로
 * 실제 OS 환경변수 대신 가짜 환경변수 출처를 써서 PC마다 결과가 같다.
 */
class WishExpiryPropertiesTest {

    @Test
    @DisplayName("환경변수가 없으면 order.yml 기본값: 켜짐 · 기한 P7D · 주기 PT1H · 유예 PT15M")
    void defaults() throws IOException {
        assertThat(bind(Map.of())).isEqualTo(new WishExpiryProperties(true, Duration.ofDays(7), Duration.ofHours(1),
            Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("환경변수로 끄기 · 기한 · 주기를 바꾼다")
    void overriddenByEnvironmentVariables() throws IOException {
        assertThat(bind(Map.of("ORDER_WISH_EXPIRY_ENABLED", "false")).enabled()).isFalse();
        assertThat(bind(Map.of("ORDER_WISH_EXPIRY_TTL", "PT2M")).ttl()).isEqualTo(Duration.ofMinutes(2));
        assertThat(bind(Map.of("ORDER_WISH_EXPIRY_INTERVAL", "PT1M")).interval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(bind(Map.of("ORDER_WISH_EXPIRY_GRACE", "PT30M")).grace()).isEqualTo(Duration.ofMinutes(30));
    }

    @ParameterizedTest(name = "{0}={1} → 기동 실패")
    @CsvSource({
        "ORDER_WISH_EXPIRY_TTL, PT0.999S, kidly.order.wish.expiry.ttl",
        "ORDER_WISH_EXPIRY_TTL, PT0S, kidly.order.wish.expiry.ttl",
        "ORDER_WISH_EXPIRY_INTERVAL, PT0.999S, kidly.order.wish.expiry.interval",
        "ORDER_WISH_EXPIRY_INTERVAL, -PT1S, kidly.order.wish.expiry.interval"
    })
    @DisplayName("기한 · 주기가 1초 미만이면 키 이름이 나오는 바인딩 오류로 기동 실패, 정확히 1초면 기동")
    void underOneSecondFails(String name, String value, String key) {
        app(Map.of(name, value)).run(context -> assertThat(context).getFailure()
            .hasRootCauseInstanceOf(BindValidationException.class)
            .rootCause().hasMessageContaining(key));
        app(Map.of(name, "PT1S")).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("유예가 음수면 키 이름이 나오는 바인딩 오류로 기동 실패, 0이면 기동")
    void negativeGraceFails() {
        app(Map.of("ORDER_WISH_EXPIRY_GRACE", "-PT1S")).run(context -> assertThat(context).getFailure()
            .hasRootCauseInstanceOf(BindValidationException.class)
            .rootCause().hasMessageContaining("kidly.order.wish.expiry.grace"));
        app(Map.of("ORDER_WISH_EXPIRY_GRACE", "PT0S")).run(context -> assertThat(context).hasNotFailed());
    }

    @EnableConfigurationProperties(WishExpiryProperties.class)
    static class ExpiryPropertiesOnly {
    }

    private static WishExpiryProperties bind(Map<String, Object> environmentVariables) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                environmentVariables));
        new YamlPropertySourceLoader().load("order.yml", new ClassPathResource("order.yml")).forEach(sources::addLast);
        return Binder.get(environment).bind("kidly.order.wish.expiry", WishExpiryProperties.class).get();
    }

    private static ApplicationContextRunner app(Map<String, Object> environmentVariables) {
        return new ApplicationContextRunner()
            .withUserConfiguration(ExpiryPropertiesOnly.class)
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
