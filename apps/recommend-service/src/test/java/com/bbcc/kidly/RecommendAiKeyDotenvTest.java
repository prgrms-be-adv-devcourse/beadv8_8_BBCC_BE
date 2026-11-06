package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

class RecommendAiKeyDotenvTest {

    @Test
    @DisplayName(".env에 OpenAI 키가 있으면 chat과 임베딩 모델을 끄지 않는다")
    void keepModelsWhenKeyIsOnlyInDotenv(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve(".env"), "OPENAI_API_KEY=sk-test-from-dotenv\n");

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EmptyConfiguration.class)
            .web(WebApplicationType.NONE)
            .properties("springdotenv.directory=" + directory, "springdotenv.enabled=true")
            .run()) {

            assertThat(context.getEnvironment().getProperty("spring.ai.openai.api-key")).isNotBlank();
            assertThat(context.getEnvironment().getProperty("spring.ai.model.chat")).isNotEqualTo("none");
            assertThat(context.getEnvironment().getProperty("spring.ai.model.embedding")).isNotEqualTo("none");
        }
    }

    @Configuration
    static class EmptyConfiguration {
    }
}
