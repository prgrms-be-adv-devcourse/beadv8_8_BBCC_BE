package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.ChildPurchase;
import java.time.LocalDateTime;

/**
 * 자녀가 보는 나를 위해 산 상품 한 줄(#1000). ID는 문자열. 가격 · 배송지 · 판매자 · 주문 상세 링크(주문 ID) · 출처는 없다
 * (출처는 화면이 조르기 응답의 paidOrderItemIds와 orderItemId를 맞춰 표시한다). 칸은 추가만 한다.
 *
 * @param status PAID · SHIPPING · DELIVERED · CONFIRMED · CANCELLED
 */
public record ChildPurchaseResponse(
    String orderItemId,
    LocalDateTime orderedAt,
    String productId,
    String productName,
    String optionName,
    int quantity,
    String status) {

    public static ChildPurchaseResponse from(ChildPurchase purchase) {
        return new ChildPurchaseResponse(String.valueOf(purchase.orderItemId()), purchase.orderedAt(),
            purchase.productId() == null ? null : String.valueOf(purchase.productId()), purchase.productName(),
            purchase.optionName(), purchase.quantity(), purchase.status());
    }
}
