package com.bbcc.kidly.boundedcontext.recommend.app.port.preference;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.util.Collection;
import java.util.Map;

/**
 * 취향 벡터와 후보 상품 벡터의 코사인 유사도를 재는 나가는 연결의 요청서다.
 */
public interface TasteSimilarityReader {

    /**
     * 취향 벡터와 각 상품 벡터의 코사인 유사도를 읽는다. 값은 -1 이상 1 이하이고 0 아래로 자르지 않는다.
     * 벡터가 없는 상품은 담지 않는다.
     */
    Map<Long, Double> similarities(EmbeddingVector taste, Collection<Long> productIds);
}
