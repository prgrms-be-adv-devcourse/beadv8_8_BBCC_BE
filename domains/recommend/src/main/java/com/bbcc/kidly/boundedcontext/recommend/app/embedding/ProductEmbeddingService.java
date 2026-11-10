package com.bbcc.kidly.boundedcontext.recommend.app.embedding;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbedder;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingStore;
import lombok.RequiredArgsConstructor;

/**
 * 글이 바뀐 상품을 묶어 임베딩하고 벡터를 저장한다.
 */
@RequiredArgsConstructor
public class ProductEmbeddingService {

    private final ProductEmbeddingStore store;
    private final ProductEmbedder embedder;

    public void embedStaleProducts() {
    }
}
