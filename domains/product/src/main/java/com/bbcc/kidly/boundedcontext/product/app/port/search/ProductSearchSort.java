package com.bbcc.kidly.boundedcontext.product.app.port.search;

/**
 * 상품 목록의 정렬이다. 정렬 값이 같으면 상품 ID가 큰 상품이 먼저 온다.
 */
public enum ProductSearchSort {
    NEWEST,
    PRICE_ASC,
    PRICE_DESC
}
