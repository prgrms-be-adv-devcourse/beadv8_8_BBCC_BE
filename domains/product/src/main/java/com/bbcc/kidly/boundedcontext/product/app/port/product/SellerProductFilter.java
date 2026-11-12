package com.bbcc.kidly.boundedcontext.product.app.port.product;

import com.bbcc.kidly.shared.product.domain.ProductStatus;

/**
 * 판매자 내 상품 목록의 조건. 두 조건은 서로 독립이고, 함께 주면 모두 만족하는 상품만 남는다.
 *
 * @param status 이 상태의 상품만 본다. null이면 모든 상태
 * @param lowStock true면 판매중 옵션 가운데 재고가 적거나 품절인 옵션이 있는 상품만 본다
 */
public record SellerProductFilter(ProductStatus status, boolean lowStock) {

    public static SellerProductFilter all() {
        return new SellerProductFilter(null, false);
    }
}
