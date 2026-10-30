package com.bbcc.kidly.boundedcontext.order.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderScheduleIntervals;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderSchedulerProperties;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentExpiryGate;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.in.scheduler.OrderScheduler;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
     * 빈 주기는 스케줄러가 주입받는 주기 설정(OrderScheduleIntervals)의 바인딩 검증이 @Scheduled 등록보다 먼저 잡는다(#252).
     * 그 전에는 스프링이 "한 번만 도는 작업"으로 보고 메서드 이름만 나오는 오류로 거절했다.
     */
    @ParameterizedTest(name = "스케줄러 주기 {0}= 이면 기동 실패, 메시지에 {1}")
    @CsvSource({
        "ORDER_PAYMENT_EXPIRY_INTERVAL, paymentExpiryInterval",
        "ORDER_AUTO_CONFIRM_INTERVAL, autoConfirm"
    })
    @DisplayName("order.yml 위에서 스케줄러 주기 환경변수를 빈 값으로 주면 NPE가 아니라 키 이름이 나오는 바인딩 오류로 실패한다")
    void emptySchedulerEnvironmentVariableFails(String name, String field) {
        appWithOrderYml(Map.of(name, ""))
            .run(context -> assertThat(context).getFailure()
                .hasRootCauseInstanceOf(BindValidationException.class)
                .rootCause().hasMessageContaining("kidly.order").hasMessageContaining(field));
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
            .withPropertyValues("kidly.order.payment-expiry-interval=1분", "kidly.order.auto-confirm.interval=PT10M",
                "kidly.order.scheduler.batch-size=100", "kidly.order.scheduler.max-per-run=1000")
            .run(context -> assertThat(context).getFailure()
                .hasStackTraceContaining("1분")
                .rootCause().isNotInstanceOf(NullPointerException.class));
    }

    @ParameterizedTest(name = "{0}={1} → 기동 실패, 메시지에 {2}")
    @CsvSource({
        "ORDER_PAYMENT_TIMEOUT, -PT1S, kidly.order.payment-timeout",
        "ORDER_PAYMENT_TIMEOUT, PT0S, kidly.order.payment-timeout",
        "ORDER_PAYMENT_TIMEOUT, PT0.999S, kidly.order.payment-timeout",
        "ORDER_AUTO_CONFIRM_AFTER, -PT1S, kidly.order.auto-confirm.after",
        "ORDER_AUTO_CONFIRM_AFTER, PT0S, kidly.order.auto-confirm.after",
        "ORDER_AUTO_CONFIRM_AFTER, PT0.999S, kidly.order.auto-confirm.after",
        "ORDER_PAYMENT_EXPIRY_INTERVAL, -PT1S, kidly.order.payment-expiry-interval",
        "ORDER_PAYMENT_EXPIRY_INTERVAL, PT0S, kidly.order.payment-expiry-interval",
        "ORDER_PAYMENT_EXPIRY_INTERVAL, PT0.999S, kidly.order.payment-expiry-interval",
        "ORDER_AUTO_CONFIRM_INTERVAL, -PT1S, kidly.order.auto-confirm.interval",
        "ORDER_AUTO_CONFIRM_INTERVAL, PT0S, kidly.order.auto-confirm.interval",
        "ORDER_AUTO_CONFIRM_INTERVAL, PT0.999S, kidly.order.auto-confirm.interval"
    })
    @DisplayName("시간 설정이 1초 미만(음수 · 0 · 0.999초)이면 NPE · 메시지 없는 오류가 아니라 키 이름이 나오는 바인딩 오류로 기동 실패")
    void timeUnderOneSecondFails(String name, String value, String key) {
        appWithOrderYml(Map.of(name, value))
            .run(context -> assertThat(context).getFailure()
                .hasRootCauseInstanceOf(BindValidationException.class)
                .rootCause().hasMessageContaining(key));
    }

    @ParameterizedTest(name = "{0}=PT1S → 기동")
    @ValueSource(strings = {
        "ORDER_PAYMENT_TIMEOUT", "ORDER_AUTO_CONFIRM_AFTER",
        "ORDER_PAYMENT_EXPIRY_INTERVAL", "ORDER_AUTO_CONFIRM_INTERVAL"})
    @DisplayName("시간 설정이 정확히 1초(PT1S)면 기동한다")
    void timeExactlyOneSecondStarts(String name) {
        appWithOrderYml(Map.of(name, "PT1S"))
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("스케줄러 건수: 환경변수가 없으면 order.yml 기본값 batch-size 100 · max-per-run 1000, 주면 그 값")
    void schedulerCountsFromOrderYml() throws IOException {
        assertThat(schedulerOf(withOrderYml(Map.of())))
            .isEqualTo(new OrderSchedulerProperties(100, 1000));
        assertThat(schedulerOf(withOrderYml(Map.of("ORDER_SCHEDULER_BATCH_SIZE", "2"))))
            .isEqualTo(new OrderSchedulerProperties(2, 1000));
        assertThat(schedulerOf(withOrderYml(Map.of("ORDER_SCHEDULER_MAX_PER_RUN", "5"))))
            .isEqualTo(new OrderSchedulerProperties(100, 5));
    }

    @Test
    @DisplayName("정책값(kidly.order)과 스케줄러 건수(kidly.order.scheduler)는 같은 접두어를 써도 서로의 키를 먹지 않고 함께 묶인다")
    void sharedPrefixBindsBoth() {
        appWithOrderYml(Map.of("ORDER_PAYMENT_TIMEOUT", "PT2M", "ORDER_SCHEDULER_BATCH_SIZE", "7"))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(OrderPolicyProperties.class).toOrderPolicy())
                    .isEqualTo(new Order.Policy(Duration.ofMinutes(2), Duration.ofDays(15)));
                assertThat(context.getBean(OrderSchedulerProperties.class))
                    .isEqualTo(new OrderSchedulerProperties(7, 1000));
            });
    }

    @ParameterizedTest(name = "{0}={1} → 기동 실패, 메시지에 {2}")
    @CsvSource({
        "ORDER_SCHEDULER_BATCH_SIZE, '', batchSize",
        "ORDER_SCHEDULER_BATCH_SIZE, 0, batchSize",
        "ORDER_SCHEDULER_BATCH_SIZE, -1, batchSize",
        "ORDER_SCHEDULER_BATCH_SIZE, 백, kidly.order.scheduler.batch-size",
        "ORDER_SCHEDULER_MAX_PER_RUN, '', maxPerRun",
        "ORDER_SCHEDULER_MAX_PER_RUN, 0, maxPerRun",
        "ORDER_SCHEDULER_MAX_PER_RUN, -1, maxPerRun",
        "ORDER_SCHEDULER_MAX_PER_RUN, 천, kidly.order.scheduler.max-per-run"
    })
    @DisplayName("스케줄러 건수가 비었거나 0 · 음수 · 숫자가 아니면 NPE가 아니라 키 이름이 나오는 바인딩 오류로 기동 실패")
    void invalidSchedulerCountFails(String name, String value, String key) {
        appWithOrderYml(Map.of(name, value))
            .run(context -> assertThat(context).getFailure()
                .hasStackTraceContaining("kidly.order.scheduler")
                .hasStackTraceContaining(key)
                .rootCause().isNotInstanceOf(NullPointerException.class));
    }

    @Test
    @DisplayName("스케줄러 건수 값이 하나도 없으면 바인딩 오류로 기동 실패")
    void missingSchedulerCountsFail() {
        new ApplicationContextRunner()
            .withUserConfiguration(SchedulerPropertiesOnly.class)
            .run(context -> assertThat(context).getFailure()
                .hasRootCauseInstanceOf(BindValidationException.class)
                .rootCause().hasMessageContaining("kidly.order.scheduler").hasMessageContaining("batchSize"));
    }

    @Test
    @DisplayName("max-per-run이 batch-size보다 1 작으면 기동 실패(두 키 이름), 같으면 통과")
    void maxPerRunAtLeastBatchSize() {
        new ApplicationContextRunner()
            .withUserConfiguration(SchedulerPropertiesOnly.class)
            .withPropertyValues("kidly.order.scheduler.batch-size=10", "kidly.order.scheduler.max-per-run=9")
            .run(context -> assertThat(context).getFailure()
                .hasRootCauseInstanceOf(BindValidationException.class)
                .rootCause().hasMessageContaining("kidly.order.scheduler.max-per-run")
                .hasMessageContaining("kidly.order.scheduler.batch-size"));
        new ApplicationContextRunner()
            .withUserConfiguration(SchedulerPropertiesOnly.class)
            .withPropertyValues("kidly.order.scheduler.batch-size=10", "kidly.order.scheduler.max-per-run=10")
            .run(context -> assertThat(context.getBean(OrderSchedulerProperties.class))
                .isEqualTo(new OrderSchedulerProperties(10, 10)));
    }

    @EnableConfigurationProperties(OrderSchedulerProperties.class)
    static class SchedulerPropertiesOnly {
    }

    private static OrderSchedulerProperties schedulerOf(StandardEnvironment environment) {
        return Binder.get(environment).bind("kidly.order.scheduler", OrderSchedulerProperties.class).get();
    }

    @EnableConfigurationProperties(OrderPolicyProperties.class)
    static class PolicyPropertiesOnly {
    }

    /**
     * 스케줄러 표현식 해석만 확인한다. 주문 서비스는 빈 결과를 돌려주는 mock.
     */
    @EnableScheduling
    @EnableConfigurationProperties({OrderSchedulerProperties.class, OrderScheduleIntervals.class})
    static class SchedulerOnly {

        @Bean
        OrderService orderService() {
            return mock(OrderService.class);
        }

        @Bean
        OrderScheduler orderScheduler(OrderService orderService, OrderSchedulerProperties properties,
            OrderScheduleIntervals intervals) {
            return new OrderScheduler(orderService, properties, intervals, mock(PaymentExpiryGate.class));
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
