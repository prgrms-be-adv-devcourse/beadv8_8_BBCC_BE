package com.bbcc.kidly.shared.product.domain;

// 등록 취소는 판매자가 더 팔지 않겠다고 내린 상태다. 판매자는 되돌릴 수 없고 관리자만 판매 중지로 되돌린다.
public enum ProductStatus {
    SALE_PENDING,
    ON_SALE,
    STOPPED,
    DELISTED
}
