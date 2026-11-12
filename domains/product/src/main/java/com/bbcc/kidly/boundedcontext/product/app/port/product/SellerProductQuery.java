package com.bbcc.kidly.boundedcontext.product.app.port.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 판매자 내 상품 목록 조회.
 */
public interface SellerProductQuery {

    // 판매자 본인의 상품 가운데 조건에 맞는 것을 등록 최신순으로 돌려준다
    Page<SellerProductSummary> findBySeller(Long sellerId, SellerProductFilter filter, Pageable pageable);
}
