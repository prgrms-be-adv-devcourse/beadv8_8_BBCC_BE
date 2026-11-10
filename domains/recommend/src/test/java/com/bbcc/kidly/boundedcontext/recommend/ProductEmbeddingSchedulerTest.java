package com.bbcc.kidly.boundedcontext.recommend;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.recommend.app.embedding.ProductEmbeddingService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingRunLock;
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
    @DisplayName("잠금을 잡으면 그 안에서 낡은 상품을 임베딩한다")
    void embedInsideLock() {
        ProductEmbeddingRunLock grantingLock = work -> {
            work.run();
            return true;
        };

        new ProductEmbeddingScheduler(grantingLock, embeddingService).embedStaleProducts();

        verify(embeddingService).embedStaleProducts();
    }

    @Test
    @DisplayName("다른 서버가 잠금을 쥐고 있어 건너뛰면 낡은 상품을 임베딩하지 않는다")
    void skipEmbeddingWhenLockIsHeldElsewhere() {
        ProductEmbeddingRunLock busyLock = work -> false;

        new ProductEmbeddingScheduler(busyLock, embeddingService).embedStaleProducts();

        verifyNoInteractions(embeddingService);
    }
}
