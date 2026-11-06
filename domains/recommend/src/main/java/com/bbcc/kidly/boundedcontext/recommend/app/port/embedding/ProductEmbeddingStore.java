package com.bbcc.kidly.boundedcontext.recommend.app.port.embedding;

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
}
