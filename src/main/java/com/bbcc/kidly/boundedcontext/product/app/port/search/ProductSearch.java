package com.bbcc.kidly.boundedcontext.product.app.port.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 구매자 상품 목록을 검색용 표에서 조회한다. 판매중 상품만 돌려준다.
 */
public interface ProductSearch {

    Page<ProductSearchItem> search(ProductSearchCondition condition, Pageable pageable);
}
