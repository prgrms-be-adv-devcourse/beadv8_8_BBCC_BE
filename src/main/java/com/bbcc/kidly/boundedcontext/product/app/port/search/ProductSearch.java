package com.bbcc.kidly.boundedcontext.product.app.port.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 구매자 상품 목록을 검색용 표에서 조회한다. 판매중 상품만 돌려준다.
 * 정렬은 조건의 sort로만 정하고, pageable의 정렬은 쓰지 않는다. pageable에서는 페이지 번호와 크기만 읽는다.
 */
public interface ProductSearch {

    Page<ProductSearchItem> search(ProductSearchCondition condition, Pageable pageable);
}
