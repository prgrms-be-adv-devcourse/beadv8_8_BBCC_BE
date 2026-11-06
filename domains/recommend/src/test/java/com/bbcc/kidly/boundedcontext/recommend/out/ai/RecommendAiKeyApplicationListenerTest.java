package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

class RecommendAiKeyApplicationListenerTest {

    private static final String API_KEY = "spring.ai.openai.api-key";
    private static final String CHAT_MODEL = "spring.ai.model.chat";
    private static final String EMBEDDING_MODEL = "spring.ai.model.embedding";

    private final RecommendAiKeyApplicationListener listener = new RecommendAiKeyApplicationListener();

    private void prepare(MockEnvironment environment) {
        listener.onApplicationEvent(new ApplicationEnvironmentPreparedEvent(
            new DefaultBootstrapContext(), new SpringApplication(), new String[0], environment));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("OpenAI 키가 없거나 비어 있으면 chat과 임베딩 모델을 끈다")
    void turnOffChatAndEmbeddingWhenKeyIsBlank(String key) {
        MockEnvironment environment = new MockEnvironment();
        if (key != null) {
            environment.setProperty(API_KEY, key);
        }

        prepare(environment);

        assertThat(environment.getProperty(CHAT_MODEL)).isEqualTo("none");
        assertThat(environment.getProperty(EMBEDDING_MODEL)).isEqualTo("none");
    }

    @Test
    @DisplayName("OpenAI 키가 있으면 chat과 임베딩 모델 설정을 건드리지 않는다")
    void keepModelsWhenKeyExists() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(API_KEY, "test-key");

        prepare(environment);

        assertThat(environment.getProperty(CHAT_MODEL)).isNull();
        assertThat(environment.getProperty(EMBEDDING_MODEL)).isNull();
    }

    @Test
    @DisplayName("설정 파일이 모델을 켜 두어도 키가 없으면 끈다")
    void turnOffEvenWhenConfigEnablesModels() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(CHAT_MODEL, "openai");
        environment.setProperty(EMBEDDING_MODEL, "openai");

        prepare(environment);

        assertThat(environment.getProperty(CHAT_MODEL)).isEqualTo("none");
        assertThat(environment.getProperty(EMBEDDING_MODEL)).isEqualTo("none");
    }
}
