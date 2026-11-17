package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;

/**
 * 구매자 화면에 보여 줄 옵션의 재고 표시다.
 *
 * @param purchasable 구매자가 이 옵션을 살 수 있는지
 * @param status 재고 상태
 * @param quantity 남은 수량. 상태가 {@link StockStatus#LOW}일 때만 값이 있고, 그 밖에는 null이다
 */
public record StockDisplay(boolean purchasable, StockStatus status, Integer quantity) {

    public static final int LOW_STOCK_THRESHOLD = 5;

    public static StockDisplay of(ProductOption option, int availableQty) {
        if (availableQty < 0) {
            throw new IllegalArgumentException("판매 가능 수량은 0 이상이어야 합니다");
        }
        boolean purchasable = option.isPurchasable() && availableQty > 0;
        if (availableQty == 0) {
            return new StockDisplay(purchasable, StockStatus.SOLD_OUT, null);
        }
        if (availableQty <= LOW_STOCK_THRESHOLD) {
            return new StockDisplay(purchasable, StockStatus.LOW, availableQty);
        }
        return new StockDisplay(purchasable, StockStatus.AVAILABLE, null);
    }
}
