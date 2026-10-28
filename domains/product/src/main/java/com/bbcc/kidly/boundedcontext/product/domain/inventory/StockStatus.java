package com.bbcc.kidly.boundedcontext.product.domain.inventory;

/**
 * 구매자 화면에 보여 줄 옵션의 재고 상태다.
 */
public enum StockStatus {
    // 구매할 수 있다
    AVAILABLE,
    // 판매 가능 수량이 기준 수 이하라 남은 수량을 보여 준다
    LOW,
    // 품절이다
    SOLD_OUT
}
