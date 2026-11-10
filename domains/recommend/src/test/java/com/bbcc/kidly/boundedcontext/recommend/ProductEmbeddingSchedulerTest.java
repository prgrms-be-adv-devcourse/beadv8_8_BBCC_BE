package com.bbcc.kidly.boundedcontext.recommend;

import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.recommend.app.embedding.ProductEmbeddingService;
import com.bbcc.kidly.boundedcontext.recommend.in.scheduler.ProductEmbeddingScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductEmbeddingSchedulerTest {

    @Mock
    private ProductEmbeddingService embeddingService;

    @Test
    @DisplayName("주기마다 낡은 상품 임베딩을 서비스에 맡긴다")
    void delegateToService() {
        new ProductEmbeddingScheduler(embeddingService).embedStaleProducts();

        verify(embeddingService).embedStaleProducts();
    }
}
