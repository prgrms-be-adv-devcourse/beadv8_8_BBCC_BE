package com.bbcc.kidly.boundedcontext.search.domain;

/**
 * 검색 문서 저장소다. 구현은 out에 둔다(Elasticsearch).
 */
public interface SearchProductRepository {

    /**
     * 같은 상품의 저장된 문서가 없거나, 저장된 문서보다 occurredAt이 늦을 때만 덮어쓴다.
     * 같은 상품의 이벤트가 동시에 와도 늦은 것이 남도록 비교와 저장을 한 번에 한다.
     */
    void saveIfNewer(SearchProduct product);
}
