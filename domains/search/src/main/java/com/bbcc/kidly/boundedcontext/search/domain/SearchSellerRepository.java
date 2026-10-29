package com.bbcc.kidly.boundedcontext.search.domain;

/**
 * 검색 판매자 저장소다. 구현은 out에 둔다(Elasticsearch).
 */
public interface SearchSellerRepository {

    /**
     * 같은 판매자의 저장된 값이 없거나, 저장된 값보다 occurredAt이 늦을 때만 덮어쓴다.
     * 같은 판매자의 이벤트가 동시에 와도 늦은 것이 남도록 비교와 저장을 한 번에 한다.
     */
    void saveIfNewer(SearchSeller seller);
}
