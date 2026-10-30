package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 주문 Kafka 수신 스위치(#541)가 order.yml 기본값(켜짐)과 환경변수로 정해지는지. 실제 OS 환경변수 대신 가짜 환경변수 출처를 쓴다.
 */
class OrderKafkaConsumerSettingsTest {

    @Test
    @DisplayName("환경변수가 없으면 order.yml 기본값: 켜짐(주문은 상품 · 판매자 · 자녀 이벤트를 Kafka로 받는다)")
    void onByDefault() {
        appWithOrderYml(Map.of()).run(context ->
            assertThat(context.getBean(OrderKafkaConsumerProperties.class).enabled()).isTrue());
    }

    @Test
    @DisplayName("ORDER_KAFKA_CONSUMER_ENABLED=false면 꺼짐(장애 때 수신을 멈춘다)")
    void offByEnvironmentVariable() {
        appWithOrderYml(Map.of("ORDER_KAFKA_CONSUMER_ENABLED", "false")).run(context ->
            assertThat(context.getBean(OrderKafkaConsumerProperties.class).enabled()).isFalse());
    }

    @EnableConfigurationProperties(OrderKafkaConsumerProperties.class)
    static class ConsumerPropertiesOnly {
    }

    /**
     * 실제 앱처럼 order.yml을 읽고 가짜 환경변수를 그 위에 둔 컨텍스트.
     */
    private static ApplicationContextRunner appWithOrderYml(Map<String, Object> environmentVariables) {
        return new ApplicationContextRunner()
            .withUserConfiguration(ConsumerPropertiesOnly.class)
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
