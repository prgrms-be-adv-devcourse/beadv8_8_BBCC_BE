package com.bbcc.kidly.boundedcontext.order.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 다른 컨텍스트가 읽는 주문 한 건(#617). 상태 · 구매확정 종류는 enum 이름 문자열.
 *
 * @param orderedAt 주문 시각(주문 생성 시각)
 * @param shippingAddress 배송지. 없으면 null
 */
public record OrderView(
    Long orderId,
    Long memberId,
    LocalDateTime orderedAt,
    ShippingAddress shippingAddress,
    List<Item> items) {

    public record ShippingAddress(String recipientName, String phone, String zipCode, String address) {
    }

    /**
     * @param child 자녀를 고르지 않았으면 null
     */
    public record Item(
        Long orderItemId,
        Long productId,
        Long optionId,
        Long sellerId,
        String productName,
        String optionName,
        String size,
        long unitPrice,
        int quantity,
        long amount,
        String status,
        LocalDateTime deliveredAt,
        LocalDateTime confirmedAt,
        String confirmType,
        Child child,
        String unavailableReason) {
    }

    public record Child(Long childId, String name, BigDecimal heightCm, BigDecimal weightKg, String bodyType) {
    }
}
