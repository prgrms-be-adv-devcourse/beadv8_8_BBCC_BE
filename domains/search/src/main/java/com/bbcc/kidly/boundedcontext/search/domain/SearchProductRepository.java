package com.bbcc.kidly.boundedcontext.search.domain;

import java.util.List;
import java.util.Optional;

/**
 * 검색 문서 저장소다. 구현은 out에 둔다(Elasticsearch).
 */
public interface SearchProductRepository {

    /**
     * 같은 상품의 저장된 문서가 없거나, 저장된 문서보다 occurredAt이 늦을 때만 덮어쓴다.
     * 같은 상품의 이벤트가 동시에 와도 늦은 것이 남도록 비교와 저장을 한 번에 한다.
     */
    void saveIfNewer(SearchProduct product);

    /**
     * 저장된 상품 문서의 의미 검색 벡터를 찾는다. 문서가 없거나 벡터 없이 저장된 문서면 비어 있다.
     */
    Optional<ProductEmbedding> findEmbedding(Long productId);

    /**
     * 벡터 없이 저장된 판매중 상품 중 문장이 함께 저장된 것을 상품 ID 순으로 limit개까지 찾는다.
     * 문장 없이 저장된 예전 문서는 찾지 않는다(상품 다시 발행으로 채운다).
     */
    List<EmbeddingRefillTarget> findOnSaleWithoutEmbedding(int limit);

    /**
     * 찾은 뒤 문서가 바뀌지 않았을 때만(같은 이벤트로 만든 문서이고 아직 벡터가 없음) 벡터를 붙인다.
     * 그사이 새 이벤트로 문서가 바뀌었으면 붙이지 않는다. 그 이벤트가 벡터를 새로 만든다.
     */
    void attachEmbedding(EmbeddingRefillTarget target, ProductEmbedding embedding);
}
