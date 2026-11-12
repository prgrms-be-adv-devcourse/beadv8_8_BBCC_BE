package com.bbcc.kidly.shared.search.out;

import com.bbcc.kidly.shared.search.dto.ProductHit;
import com.bbcc.kidly.shared.search.dto.ProductSearchQuery;
import java.util.List;

/**
 * 판매중 상품 검색(#1191). 검색 도메인이 구현한다. 서비스가 나뉘어 있으면 검색 서비스의 내부 경로
 * GET /internal/products/search?keyword=&minPrice=&maxPrice=&limit=가 같은 모양(JSON 배열)으로 답한다(밖에서는 Gateway가
 * /internal/**을 막는다).
 */
public interface ProductSearcher {

    /**
     * 관련도순으로 최대 limit개. 없으면 빈 목록이다. 검색 색인을 쓸 수 없으면 RuntimeException을 던진다(내부 경로는 503).
     */
    List<ProductHit> search(ProductSearchQuery query);
}
