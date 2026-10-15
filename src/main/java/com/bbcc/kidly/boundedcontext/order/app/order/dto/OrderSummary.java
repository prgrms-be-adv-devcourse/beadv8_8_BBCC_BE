package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * 주문 목록의 한 줄. 상태는 enum 이름 문자열.
 *
 * @param orderedAt 주문 시각(생성 시각)
 * @param representativeProductName 대표 상품명 = 주문상품 ID가 가장 작은 주문상품의 주문 당시 상품명. 주문상품이 없으면 null
 * @param itemCount 주문상품 수(행 수, 수량 합 아님)
 */
public record OrderSummary(
    Long orderId,
    String status,
    long totalAmount,
    LocalDateTime orderedAt,
    String representativeProductName,
    int itemCount) {

    /**
     * 주문상품을 함께 읽은 주문으로 만든다. 대표 상품은 주문상품 ID가 가장 작은 것(목록 정렬 순서에 기대지 않음).
     */
    public static OrderSummary from(Order order) {
        String representative = order.getItems().stream()
            .min(Comparator.comparing(OrderItem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(item -> item.getProduct().productName())
            .orElse(null);
        return new OrderSummary(order.getId(), order.getStatus().name(), order.getTotalAmount(), order.getCreatedAt(),
            representative, order.getItems().size());
    }

    /**
     * 주문상품을 읽지 못한 주문. 대표 상품명 null · 주문상품 수 0.
     */
    public static OrderSummary withoutItems(Order order) {
        return new OrderSummary(order.getId(), order.getStatus().name(), order.getTotalAmount(), order.getCreatedAt(),
            null, 0);
    }
}
