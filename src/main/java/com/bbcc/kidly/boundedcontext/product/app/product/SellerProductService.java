package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductQuery;
import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 본인의 상품 조회 유스케이스.
 */
@Service
@RequiredArgsConstructor
public class SellerProductService {

    private final SellerProductQuery sellerProductQuery;

    // 정렬은 등록 최신순으로 고정이라 pageable의 sort는 무시된다
    @Transactional(readOnly = true)
    public Page<SellerProductSummary> findMyProducts(Long sellerId, Pageable pageable) {
        return sellerProductQuery.findBySeller(sellerId, pageable);
    }
}
