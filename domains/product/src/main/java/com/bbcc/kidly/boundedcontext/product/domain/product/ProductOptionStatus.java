package com.bbcc.kidly.boundedcontext.product.domain.product;

/**
 * 옵션의 판매 상태다. 판매중, 판매중지, 보관 중 하나다.
 */
public enum ProductOptionStatus {
    ON_SALE, // 판매중
    STOPPED, // 판매중지
    ARCHIVED // 보관
}
