package com.bbcc.kidly.boundedcontext.product.app.port.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 판매자 내 상품 목록 조회.
 */
public interface SellerProductQuery {

    // 판매자 본인의 상품을 판매 상태와 상관없이 등록 최신순으로 돌려준다
    Page<SellerProductSummary> findBySeller(Long sellerId, Pageable pageable);
}
