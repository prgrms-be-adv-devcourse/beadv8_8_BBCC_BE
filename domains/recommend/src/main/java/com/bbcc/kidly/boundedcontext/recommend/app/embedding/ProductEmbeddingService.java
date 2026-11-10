package com.bbcc.kidly.boundedcontext.recommend.app.embedding;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbedder;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingTarget;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 글이 바뀐 상품을 묶어 임베딩하고 벡터를 저장한다.
 */
@RequiredArgsConstructor
public class ProductEmbeddingService {

    private static final int BATCH_SIZE = 100;

    private final ProductEmbeddingStore store;
    private final ProductEmbedder embedder;

    public void embedStaleProducts() {
        List<ProductEmbeddingTarget> targets = store.findStale(BATCH_SIZE);
        if (targets.isEmpty()) {
            return;
        }
        embedTogether(targets);
    }

    private void embedTogether(List<ProductEmbeddingTarget> targets) {
        List<Long> productIds = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (ProductEmbeddingTarget target : targets) {
            productIds.add(target.productId());
            texts.add(target.text());
        }
        store.markAttempted(productIds);
        List<EmbeddingVector> vectors = embedder.embed(texts);
        for (int i = 0; i < targets.size(); i++) {
            store.saveIfTextUnchanged(productIds.get(i), texts.get(i), vectors.get(i));
        }
    }
}
