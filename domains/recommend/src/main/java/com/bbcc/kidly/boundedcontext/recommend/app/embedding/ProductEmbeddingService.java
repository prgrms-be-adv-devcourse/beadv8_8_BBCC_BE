package com.bbcc.kidly.boundedcontext.recommend.app.embedding;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbedder;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingRunLock;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingTarget;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 글이 바뀐 상품을 묶어 임베딩하고 벡터를 저장한다.
 * 이 서비스는 트랜잭션을 걸지 않는다. 저장소 메서드가 각자 바로 커밋되고 OpenAI 호출은 트랜잭션 밖에서 한다.
 * Spring AI가 OpenAI 임베딩 모델 빈을 만드는 조건과 같을 때만 뜬다(키가 없으면 모델 빈이 없다).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "openai", matchIfMissing = true)
@RequiredArgsConstructor
public class ProductEmbeddingService {

    private static final int BATCH_SIZE = 100;

    private final ProductEmbeddingStore store;
    private final ProductEmbedder embedder;
    private final ProductEmbeddingRunLock runLock;

    public void embedStaleProducts() {
        if (!runLock.runExclusively(this::embedOneBatch)) {
            log.debug("다른 서버가 상품 임베딩을 실행 중이라 건너뜁니다");
        }
    }

    private void embedOneBatch() {
        List<ProductEmbeddingTarget> targets = store.findStale(BATCH_SIZE);
        List<ProductEmbeddingTarget> neverAttempted = new ArrayList<>();
        List<ProductEmbeddingTarget> retried = new ArrayList<>();
        for (ProductEmbeddingTarget target : targets) {
            if (target.retried()) {
                retried.add(target);
            } else {
                neverAttempted.add(target);
            }
        }
        if (!neverAttempted.isEmpty() && !embedTogether(neverAttempted)) {
            return;
        }
        for (ProductEmbeddingTarget target : retried) {
            if (!embedTogether(List.of(target))) {
                return;
            }
        }
    }

    private boolean embedTogether(List<ProductEmbeddingTarget> targets) {
        List<Long> productIds = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (ProductEmbeddingTarget target : targets) {
            productIds.add(target.productId());
            texts.add(target.text());
        }
        store.markAttempted(productIds);
        List<EmbeddingVector> vectors;
        try {
            vectors = embedder.embed(texts);
        } catch (RuntimeException e) {
            // 예외 메시지와 스택 트레이스에는 임베딩 API의 응답 본문이 들어갈 수 있어 예외 종류만 남긴다
            log.warn("상품 임베딩 요청이 실패했습니다. count={}, error={}", targets.size(), e.getClass().getSimpleName());
            return false;
        }
        for (int i = 0; i < targets.size(); i++) {
            store.saveIfTextUnchanged(productIds.get(i), texts.get(i), vectors.get(i));
        }
        return true;
    }
}
