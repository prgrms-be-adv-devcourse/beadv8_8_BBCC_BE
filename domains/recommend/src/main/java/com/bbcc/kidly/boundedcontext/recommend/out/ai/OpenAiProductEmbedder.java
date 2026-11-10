package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbedder;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Spring AI 임베딩 모델(OpenAI)로 상품 글을 벡터로 바꾼다.
 */
@RequiredArgsConstructor
public class OpenAiProductEmbedder implements ProductEmbedder {

    private final EmbeddingModel embeddingModel;

    @Override
    public List<EmbeddingVector> embed(List<String> texts) {
        List<float[]> embeddings = embeddingModel.embed(texts);
        if (embeddings.size() != texts.size()) {
            throw new IllegalStateException(
                "임베딩 개수가 글 수와 다릅니다: texts=" + texts.size() + ", embeddings=" + embeddings.size());
        }
        List<EmbeddingVector> vectors = new ArrayList<>();
        for (float[] embedding : embeddings) {
            vectors.add(new EmbeddingVector(embedding));
        }
        return vectors;
    }
}
