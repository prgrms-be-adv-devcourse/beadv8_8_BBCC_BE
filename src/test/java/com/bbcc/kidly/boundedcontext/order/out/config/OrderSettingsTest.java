package com.bbcc.kidly.boundedcontext.order.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.in.scheduler.OrderScheduler;
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
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * order.yml의 기본값과 환경변수가 주문 정책(Order.Policy) · 스케줄러 주기에 들어가는지 확인한다.
 * 실제 OS 환경변수 대신 가짜 환경변수 출처를 써서 PC마다 결과가 같다.
 */
class OrderSettingsTest {

    @Test
    @DisplayName("환경변수가 없으면 order.yml 기본값: 결제 마감 PT10M · 만료 주기 PT1M · 자동 확정 P15D · 확정 주기 PT10M")
    void usesDefaults() throws IOException {
        Settings settings = settings(withOrderYml(Map.of()));

        assertThat(settings).isEqualTo(new Settings(
            Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofDays(15), Duration.ofMinutes(10)));
    }

    @ParameterizedTest(name = "{0}={1}이면 그 값만 바뀐다")
    @CsvSource({
        "ORDER_PAYMENT_TIMEOUT, PT2M",
        "ORDER_PAYMENT_EXPIRY_INTERVAL, PT10S",
        "ORDER_AUTO_CONFIRM_AFTER, PT3M",
        "ORDER_AUTO_CONFIRM_INTERVAL, PT30S"
    })
    @DisplayName("환경변수를 하나 주면 그 값만 바뀌고 나머지는 기본값이다")
    void overridesOneByEnvironmentVariable(String name, String value) throws IOException {
        Settings settings = settings(withOrderYml(Map.of(name, value)));

        Duration changed = Duration.parse(value);
        assertThat(settings).isEqualTo(new Settings(
            name.equals("ORDER_PAYMENT_TIMEOUT") ? changed : Duration.ofMinutes(10),
            name.equals("ORDER_PAYMENT_EXPIRY_INTERVAL") ? changed : Duration.ofMinutes(1),
            name.equals("ORDER_AUTO_CONFIRM_AFTER") ? changed : Duration.ofDays(15),
            name.equals("ORDER_AUTO_CONFIRM_INTERVAL") ? changed : Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("스케줄러 주기는 코드에 기본값이 없다: order.yml이 없으면 주기를 정할 수 없다")
    void schedulerHasNoDefaultInCode() {
        StandardEnvironment environment = emptyEnvironment(Map.of());

        assertThatThrownBy(() -> environment.resolveRequiredPlaceholders(fixedDelay("autoConfirm")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> environment.resolveRequiredPlaceholders(fixedDelay("expireOverduePayments")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정책값은 코드에 기본값이 없다: 값이 하나도 없으면 바인딩 오류로 기동에 실패하고 키 이름이 나온다")
    void policyHasNoDefaultInCode() {
        new ApplicationContextRunner()
            .withUserConfiguration(PolicyPropertiesOnly.class)
            .run(context -> assertThat(context).getFailure()
                .hasRootCauseInstanceOf(BindValidationException.class)
                .rootCause().hasMessageContaining("kidly.order").hasMessageContaining("paymentTimeout"));
    }

    @Test
    @DisplayName("자동 구매확정 기간(after)만 빠져도 바인딩 오류로 기동에 실패하고 키 이름(autoConfirm)이 나온다")
    void missingAutoConfirmAfterFails() {
        new ApplicationContextRunner()
            .withUserConfiguration(PolicyPropertiesOnly.class)
            .withPropertyValues("kidly.order.payment-timeout=PT10M", "kidly.order.auto-confirm.interval=PT10M")
            .run(context -> assertThat(context).getFailure()
                .hasRootCauseInstanceOf(BindValidationException.class)
                .rootCause().hasMessageContaining("kidly.order").hasMessageContaining("autoConfirm"));
    }

    @ParameterizedTest(name = "정책값 {0}= 이면 바인딩 오류, 메시지에 {1}")
    @CsvSource({
        "ORDER_PAYMENT_TIMEOUT, paymentTimeout",
        "ORDER_AUTO_CONFIRM_AFTER, autoConfirm"
    })
    @DisplayName("order.yml 위에서 정책 환경변수를 빈 값으로 주면 NPE가 아니라 키 이름이 나오는 바인딩 오류로 실패한다")
    void emptyPolicyEnvironmentVariableFails(String name, String field) {
        appWithOrderYml(Map.of(name, ""))
            .run(context -> assertThat(context).getFailure()
                .hasRootCauseInstanceOf(BindValidationException.class)
                .rootCause().hasMessageContaining("kidly.order").hasMessageContaining(field));
    }

    /**
     * 빈 주기는 스프링이 "한 번만 도는 작업"으로 보고 시작 지연이 없어 거절한다. 메시지에는 키 대신 메서드 이름이 나온다.
     */
    @ParameterizedTest(name = "스케줄러 주기 {0}= 이면 기동 실패, 메시지에 메서드 {1}")
    @CsvSource({
        "ORDER_PAYMENT_EXPIRY_INTERVAL, expireOverduePayments",
        "ORDER_AUTO_CONFIRM_INTERVAL, autoConfirm"
    })
    @DisplayName("order.yml 위에서 스케줄러 주기 환경변수를 빈 값으로 주면 NPE가 아니라 기동 오류로 실패한다")
    void emptySchedulerEnvironmentVariableFails(String name, String method) {
        appWithOrderYml(Map.of(name, ""))
            .run(context -> assertThat(context).getFailure()
                .hasStackTraceContaining("Encountered invalid @Scheduled method '" + method + "'")
                .rootCause().isNotInstanceOf(NullPointerException.class));
    }

    @Test
    @DisplayName("정책값 형식이 틀리면(10분) NPE가 아니라 키 이름이 나오는 바인딩 오류로 실패한다")
    void malformedPolicyValueFails() {
        new ApplicationContextRunner()
            .withUserConfiguration(PolicyPropertiesOnly.class)
            .withPropertyValues("kidly.order.payment-timeout=10분", "kidly.order.auto-confirm.after=P15D")
            .run(context -> assertThat(context).getFailure()
                .hasStackTraceContaining("kidly.order.payment-timeout")
                .rootCause().isNotInstanceOf(NullPointerException.class));
    }

    @Test
    @DisplayName("스케줄러 주기 형식이 틀리면(1분) NPE가 아니라 기동 오류로 실패하고 값이 나온다")
    void malformedSchedulerIntervalFails() {
        new ApplicationContextRunner()
            .withUserConfiguration(SchedulerOnly.class)
            .withPropertyValues("kidly.order.payment-expiry-interval=1분", "kidly.order.auto-confirm.interval=PT10M")
            .run(context -> assertThat(context).getFailure()
                .hasStackTraceContaining("1분")
                .rootCause().isNotInstanceOf(NullPointerException.class));
    }

    @EnableConfigurationProperties(OrderPolicyProperties.class)
    static class PolicyPropertiesOnly {
    }

    /**
     * 스케줄러 표현식 해석만 확인한다. 주문 서비스는 빈 결과를 돌려주는 mock.
     */
    @EnableScheduling
    static class SchedulerOnly {

        @Bean
        OrderService orderService() {
            return mock(OrderService.class);
        }

        @Bean
        OrderScheduler orderScheduler(OrderService orderService) {
            return new OrderScheduler(orderService);
        }
    }

    private record Settings(Duration paymentTimeout, Duration paymentExpiryInterval, Duration autoConfirmAfter,
        Duration autoConfirmInterval) {
    }

    private static Settings settings(StandardEnvironment environment) {
        Order.Policy policy = Binder.get(environment).bind("kidly.order", OrderPolicyProperties.class).get()
            .toOrderPolicy();
        return new Settings(
            policy.paymentTimeout(),
            Duration.parse(environment.resolveRequiredPlaceholders(fixedDelay("expireOverduePayments"))),
            policy.autoConfirmAfter(),
            Duration.parse(environment.resolveRequiredPlaceholders(fixedDelay("autoConfirm"))));
    }

    private static String fixedDelay(String method) {
        try {
            return OrderScheduler.class.getMethod(method).getAnnotation(Scheduled.class).fixedDelayString();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 실제 앱처럼 order.yml을 읽고 가짜 환경변수를 그 위에 둔 컨텍스트. 정책값 바인딩과 스케줄러 등록을 함께 한다.
     */
    private static ApplicationContextRunner appWithOrderYml(Map<String, Object> environmentVariables) {
        return new ApplicationContextRunner()
            .withUserConfiguration(PolicyPropertiesOnly.class, SchedulerOnly.class)
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

    private static StandardEnvironment withOrderYml(Map<String, Object> environmentVariables) throws IOException {
        StandardEnvironment environment = emptyEnvironment(environmentVariables);
        new YamlPropertySourceLoader().load("order.yml", new ClassPathResource("order.yml"))
            .forEach(environment.getPropertySources()::addLast);
        return environment;
    }

    /**
     * 실제 OS 환경변수 · 시스템 속성을 빼고 가짜 환경변수만 둔다.
     */
    private static StandardEnvironment emptyEnvironment(Map<String, Object> environmentVariables) {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new SystemEnvironmentPropertySource(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables));
        return environment;
    }
}
