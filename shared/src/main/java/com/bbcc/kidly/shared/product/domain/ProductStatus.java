package com.bbcc.kidly.shared.product.domain;

// 상품 판매 상태. 판매 시작 전(SALE_PENDING), 판매 중(ON_SALE), 판매 중지(STOPPED)
public enum ProductStatus {
    SALE_PENDING,
    ON_SALE,
    STOPPED
}
