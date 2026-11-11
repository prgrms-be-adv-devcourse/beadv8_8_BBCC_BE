package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.AiSizeChooser;
import com.bbcc.kidly.boundedcontext.recommend.in.scheduler.ProductEmbeddingScheduler;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

@IntegrationTest
class RecommendAiModelIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("OpenAI 키가 없는 테스트 컨텍스트에는 chat과 임베딩 모델 빈이 없다")
    void noModelBeansWithoutKey() {
        assertThat(context.getBeanNamesForType(ChatModel.class)).isEmpty();
        assertThat(context.getBeanNamesForType(EmbeddingModel.class)).isEmpty();
    }

    @Test
    @DisplayName("OpenAI 키가 없는 테스트 컨텍스트에는 상품 임베딩 스케줄러 빈이 없다")
    void noEmbeddingSchedulerWithoutKey() {
        assertThat(context.getBeanNamesForType(ProductEmbeddingScheduler.class)).isEmpty();
    }

    @Test
    @DisplayName("OpenAI 키가 없는 테스트 컨텍스트에는 AI 사이즈 선택 빈이 없어 사이즈 추천은 규칙 결과로 답한다")
    void noAiSizeChooserWithoutKey() {
        assertThat(context.getBeanNamesForType(AiSizeChooser.class)).isEmpty();
    }
}
