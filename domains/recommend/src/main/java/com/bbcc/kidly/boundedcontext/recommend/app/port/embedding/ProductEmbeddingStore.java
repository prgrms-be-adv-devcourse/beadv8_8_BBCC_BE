package com.bbcc.kidly.boundedcontext.recommend.app.port.embedding;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.util.Collection;
import java.util.List;

/**
 * 상품 복사본의 임베딩 벡터를 읽고 저장하는 나가는 연결의 요청서다.
 */
public interface ProductEmbeddingStore {

    /**
     * 임베딩할 글이 있고, 그 글로 만든 벡터가 없거나 글이 바뀌어 낡은 상품을 읽는다.
     * 처음 보내는 상품이 앞서 보냈다가 성공하지 못한 상품보다 먼저 나온다. 판매 상태로는 거르지 않는다.
     */
    List<ProductEmbeddingTarget> findStale(int limit);

    /**
     * 임베딩 API에 보내기 직전에, 그 상품들에 보낸 시각을 남긴다. 성공하지 못하면 이 시각이 남아 다음에 뒤로 밀린다.
     */
    void markAttempted(Collection<Long> productIds);

    /**
     * 벡터를 만든 글이 상품의 지금 글과 같을 때만 벡터를 저장하고 시도 시각을 지운다.
     * 임베딩 API를 부르는 사이에 글이 바뀌었으면 낡은 벡터를 남기지 않으려고 저장하지 않는다.
     *
     * @return 저장했으면 true, 그 사이 글이 바뀌었거나 상품이 없어서 저장하지 않았으면 false
     */
    boolean saveIfTextUnchanged(Long productId, String text, EmbeddingVector vector);
}
