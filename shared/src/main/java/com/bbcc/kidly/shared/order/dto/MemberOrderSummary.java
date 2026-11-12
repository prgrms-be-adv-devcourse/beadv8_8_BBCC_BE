package com.bbcc.kidly.shared.order.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 회원 주문 하나의 진행 상황(#1191). 상태 · 택배사는 enum 이름 문자열이라 값이 늘 수 있다(모르는 값은 그대로 보여 준다).
 *
 * @param status 주문 상태: PENDING_PAYMENT · PAID · CANCELLED
 * @param items 주문상품 ID 순
 */
public record MemberOrderSummary(Long orderId, LocalDateTime orderedAt, String status, List<Item> items) {

    /**
     * @param optionName 없으면 null
     * @param status 주문상품 상태: ORDERED · CONFIRMED(구매확정) · CANCELLED
     * @param delivery 이 상품 판매자의 배송. 결제 전이라 배송이 없으면 null
     */
    public record Item(String productName, String optionName, int quantity, String status, Delivery delivery) {
    }

    /**
     * @param status 배송 상태: READY(배송 준비) · SHIPPING · DELIVERED · CANCELLED
     * @param carrier 택배사 코드(CJ · EPOST · HANJIN · LOTTE · LOGEN 등). 발송 전이면 null
     * @param shippedAt 발송 전이면 null
     * @param deliveredAt 배송 완료 전이면 null
     */
    public record Delivery(String status, String carrier, LocalDateTime shippedAt, LocalDateTime deliveredAt) {
    }
}
