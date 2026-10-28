package com.bbcc.kidly.shared.product.domain;

// 재고 차감을 거절한 사유. 재고 부족함 이벤트의 대표 사유와 주문상품별 사유가 함께 쓴다
public enum StockDeductionFailureCode {
    // 판매 가능 수량이 요청 수량보다 적다
    OUT_OF_STOCK,
    // 상품이 판매중이 아니다
    PRODUCT_NOT_ON_SALE,
    // 상품은 판매중인데 옵션이 판매중이 아니다
    OPTION_NOT_ON_SALE
}
