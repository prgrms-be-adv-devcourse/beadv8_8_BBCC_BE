package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;

/**
 * 구매자 화면에 보여 줄 옵션의 재고 표시다.
 *
 * @param status 재고 상태
 * @param quantity 남은 수량. 상태가 {@link StockStatus#LOW}일 때만 값이 있고, 그 밖에는 null이다
 */
public record StockDisplay(StockStatus status, Integer quantity) {

    // 판매 가능 수량이 이 값 이하일 때만 구매자에게 수량을 보여 준다
    private static final int LOW_STOCK_THRESHOLD = 5;

    /**
     * 판매 가능 수량이 기준 수 이하일 때만 수량을 보여 주고, 0이면 품절로 표시한다 (SALE-09).
     */
    public static StockDisplay of(ProductOption option, int availableQty) {
        requireNonNull(option, "option은 필수입니다");
        if (availableQty < 0) {
            throw new IllegalArgumentException("판매 가능 수량은 0 이상이어야 합니다");
        }
        if (availableQty == 0) {
            return new StockDisplay(StockStatus.SOLD_OUT, null);
        }
        if (availableQty <= LOW_STOCK_THRESHOLD) {
            return new StockDisplay(StockStatus.LOW, availableQty);
        }
        return new StockDisplay(StockStatus.AVAILABLE, null);
    }
}
