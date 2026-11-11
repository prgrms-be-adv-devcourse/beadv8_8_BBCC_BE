package com.bbcc.kidly.boundedcontext.order.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.config.StuckOrderDiagnosisProperties;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;
import com.bbcc.kidly.boundedcontext.order.out.ai.DisabledDiagnoser;
import com.bbcc.kidly.boundedcontext.order.out.ai.FakeDiagnoser;
import com.bbcc.kidly.boundedcontext.order.out.ai.OpenAiCompatibleDiagnoser;
import java.io.IOException;
import java.time.Duration;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * 멈춘 주문 AI 진단 설정(#926): order.yml 기본은 꺼짐 · Groq, 키가 없으면 자동으로 꺼짐, 환경변수만 바꿔 제공자 · 주소 · 모델 전환.
 */
class OrderAiConfigTest {

    @Test
    @DisplayName("환경변수가 없으면 꺼짐(DisabledDiagnoser). 기본은 Groq 주소 · 프로덕션 모델 · 하루 200 · 응답 15초")
    void defaults() {
        appWithOrderYml(Map.of()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(StuckOrderDiagnoser.class)).isInstanceOf(DisabledDiagnoser.class);
            assertThat(context.getBean(StuckOrderDiagnoser.class).isAvailable()).isFalse();
            OrderAiProperties ai = context.getBean(OrderAiProperties.class);
            assertThat(ai.enabled()).isFalse();
            assertThat(ai.provider()).isEqualTo(OrderAiProperties.Provider.OPENAI_COMPATIBLE);
            assertThat(ai.baseUrl()).isEqualTo("https://api.groq.com/openai/v1");
            assertThat(ai.model()).isEqualTo("openai/gpt-oss-20b");
            assertThat(ai.apiKey()).isEmpty();
            assertThat(ai.timeout()).isEqualTo(Duration.ofSeconds(15));
            assertThat(context.getBean(StuckOrderDiagnosisProperties.class))
                .isEqualTo(new StuckOrderDiagnosisProperties(200, 10, 50, 20));
            assertThat(ai.maxOutputTokens()).isEqualTo(800);
        });
    }

    @Test
    @DisplayName("켜도 키가 없거나 공백 · 헤더에 못 넣는 값이면 자동으로 꺼진다")
    void offWithoutKey() {
        for (String key : new String[] {"", "  ", "키값"}) {
            appWithOrderYml(Map.of("ORDER_AI_ENABLED", "true", "ORDER_AI_API_KEY", key)).run(context ->
                assertThat(context.getBean(StuckOrderDiagnoser.class)).isInstanceOf(DisabledDiagnoser.class));
        }
    }

    @Test
    @DisplayName("켜고 키가 있으면 OpenAI 호환 어댑터. 환경변수로 주소 · 모델을 바꾼다. toString에 키가 없다")
    void openAiCompatibleWithKey() {
        appWithOrderYml(Map.of("ORDER_AI_ENABLED", "true", "ORDER_AI_API_KEY", "sk-secret",
            "ORDER_AI_BASE_URL", "https://api.openai.com/v1", "ORDER_AI_MODEL", "gpt-4o-mini")).run(context -> {
                assertThat(context.getBean(StuckOrderDiagnoser.class)).isInstanceOf(OpenAiCompatibleDiagnoser.class);
                OrderAiProperties ai = context.getBean(OrderAiProperties.class);
                assertThat(ai.baseUrl()).isEqualTo("https://api.openai.com/v1");
                assertThat(ai.model()).isEqualTo("gpt-4o-mini");
                assertThat(ai.toString()).doesNotContain("sk-secret");
            });
    }

    @Test
    @DisplayName("provider fake면 키 없이도 켜지고 외부를 부르지 않는 가짜 어댑터")
    void fakeProvider() {
        appWithOrderYml(Map.of("ORDER_AI_ENABLED", "true", "ORDER_AI_PROVIDER", "fake")).run(context ->
            assertThat(context.getBean(StuckOrderDiagnoser.class)).isInstanceOf(FakeDiagnoser.class));
    }

    @Test
    @DisplayName("모르는 제공자면 키 이름이 나오는 바인딩 오류로 기동 실패")
    void unknownProviderFails() {
        appWithOrderYml(Map.of("ORDER_AI_PROVIDER", "claude")).run(context ->
            assertThat(context).getFailure().hasStackTraceContaining("kidly.order.ai"));
    }

    @EnableConfigurationProperties({OrderAiProperties.class, StuckOrderDiagnosisProperties.class})
    static class AiOnly {
    }

    private static ApplicationContextRunner appWithOrderYml(Map<String, Object> environmentVariables) {
        return new ApplicationContextRunner()
            .withUserConfiguration(AiOnly.class, OrderAiConfig.class)
            .withBean(JsonMapper.class, JsonMapper::shared)
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
