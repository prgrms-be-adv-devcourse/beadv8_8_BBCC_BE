package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.recommend.app.embedding.ProductEmbeddingService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingRunLock;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingStore;
import com.bbcc.kidly.boundedcontext.recommend.in.scheduler.ProductEmbeddingScheduler;
import com.bbcc.kidly.boundedcontext.recommend.out.ai.OpenAiProductEmbedder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 상품 임베딩 작업은 Spring AI가 임베딩 모델 빈을 만드는 조건과 같을 때만 뜬다.
 * 키가 없으면 모델 빈이 없어 이 작업의 빈이 뜨면 기동이 실패하기 때문이다.
 */
class ProductEmbeddingBeanConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(SchedulingEnabled.class, ProductEmbeddingScheduler.class,
            ProductEmbeddingService.class, OpenAiProductEmbedder.class)
        .withBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class))
        .withBean(ProductEmbeddingStore.class, () -> mock(ProductEmbeddingStore.class))
        .withBean(ProductEmbeddingRunLock.class, () -> mock(ProductEmbeddingRunLock.class));

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "openai")
    @DisplayName("임베딩 모델 설정이 없거나 openai이면 상품 임베딩 스케줄러 · 서비스 · 어댑터 빈이 뜬다")
    void createBeansWhenEmbeddingModelIsOpenAi(String embeddingModel) {
        ApplicationContextRunner configured = embeddingModel == null
            ? runner
            : runner.withPropertyValues("spring.ai.model.embedding=" + embeddingModel);

        configured.run(context -> {
            assertThat(context).hasSingleBean(ProductEmbeddingScheduler.class);
            assertThat(context).hasSingleBean(ProductEmbeddingService.class);
            assertThat(context).hasSingleBean(OpenAiProductEmbedder.class);
        });
    }

    @Test
    @DisplayName("임베딩 모델 설정이 none이면 상품 임베딩 스케줄러 · 서비스 · 어댑터 빈이 뜨지 않는다")
    void skipBeansWhenEmbeddingModelIsNone() {
        runner.withPropertyValues("spring.ai.model.embedding=none").run(context -> {
            assertThat(context).doesNotHaveBean(ProductEmbeddingScheduler.class);
            assertThat(context).doesNotHaveBean(ProductEmbeddingService.class);
            assertThat(context).doesNotHaveBean(OpenAiProductEmbedder.class);
        });
    }

    // 스케줄러의 주기 기본값을 스프링이 읽을 수 있는지도 컨텍스트가 뜨는 것으로 확인한다. 첫 실행이 한 주기 뒤라 테스트 중에는 돌지 않는다
    @Configuration
    @EnableScheduling
    static class SchedulingEnabled {
    }
}
