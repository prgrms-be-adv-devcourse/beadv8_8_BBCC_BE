package com.bbcc.kidly.boundedcontext.search.app.port;

/**
 * 상품 목록의 정렬이다. 정렬 값이 같으면 상품 ID가 큰 상품이 먼저 온다.
 * RELEVANCE는 검색어와 가까운 순이다. 상품명 · 브랜드에 검색어가 있는 상품과 뜻이 가까운 상품(의미 검색)을 함께 찾는다.
 * 검색어가 없으면 NEWEST와 같다.
 */
public enum ProductSearchSort {
    NEWEST,
    PRICE_ASC,
    PRICE_DESC,
    RELEVANCE
}
