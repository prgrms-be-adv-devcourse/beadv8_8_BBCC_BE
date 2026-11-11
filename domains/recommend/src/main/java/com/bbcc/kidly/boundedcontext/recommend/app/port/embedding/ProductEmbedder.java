package com.bbcc.kidly.boundedcontext.recommend.app.port.embedding;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.util.List;

/**
 * 상품 글을 벡터로 바꾸는 나가는 연결의 요청서다.
 */
public interface ProductEmbedder {

    /**
     * 글들을 한 번의 요청으로 벡터로 바꾼다. 받은 글과 같은 순서 · 같은 개수로 돌려준다.
     * 요청이 실패하면 모든 글이 실패한다.
     *
     * @throws RuntimeException 요청이 실패했을 때
     */
    List<EmbeddingVector> embed(List<String> texts);
}
