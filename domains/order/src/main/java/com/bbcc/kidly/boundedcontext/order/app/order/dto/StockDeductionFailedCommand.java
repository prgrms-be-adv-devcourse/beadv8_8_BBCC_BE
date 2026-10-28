package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import java.util.List;

/**
 * 재고 차감 실패로 주문을 취소하라는 입력. 주문상품 ID는 한 번씩만, 사유는 상품이 보낸 이름 그대로.
 */
public record StockDeductionFailedCommand(
    Long orderId,
    List<Item> items) {

    public record Item(
        Long orderItemId,
        String reason) {
    }
}
