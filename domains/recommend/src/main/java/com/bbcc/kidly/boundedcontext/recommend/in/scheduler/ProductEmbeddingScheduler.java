package com.bbcc.kidly.boundedcontext.recommend.in.scheduler;

import com.bbcc.kidly.boundedcontext.recommend.app.embedding.ProductEmbeddingService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingRunLock;
import lombok.RequiredArgsConstructor;

/**
 * 글이 바뀐 상품을 주기마다 임베딩한다. 서버가 여러 대여도 잠금을 잡은 한 대만 실행한다.
 */
@RequiredArgsConstructor
public class ProductEmbeddingScheduler {

    private final ProductEmbeddingRunLock runLock;
    private final ProductEmbeddingService embeddingService;

    public void embedStaleProducts() {
    }
}
