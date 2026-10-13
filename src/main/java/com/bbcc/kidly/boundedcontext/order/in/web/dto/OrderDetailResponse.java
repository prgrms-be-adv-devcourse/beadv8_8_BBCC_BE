package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세. ID는 문자열 · 금액은 원 단위 정수(기획서 6.1).
 *
 * @param cancelReason 취소가 아니면 null
 */
public record OrderDetailResponse(
    String orderId,
    String status,
    String cancelReason,
    long totalAmount,
    LocalDateTime orderedAt,
    LocalDateTime paymentDueAt,
    ShippingAddress shippingAddress,
    List<Item> items) {

    public record ShippingAddress(String recipientName, String phone, String zipCode, String address) {
    }

    /**
     * @param child 자녀를 고르지 않았으면 null
     */
    public record Item(
        String orderItemId,
        String productId,
        String optionId,
        String sellerId,
        String productName,
        String size,
        long unitPrice,
        int quantity,
        long amount,
        String status,
        Child child) {
    }

    public record Child(String childId, String name, BigDecimal heightCm, BigDecimal weightKg, String bodyType) {
    }

    public static OrderDetailResponse from(OrderDetail detail) {
        OrderDetail.ShippingAddress address = detail.shippingAddress();
        return new OrderDetailResponse(
            toText(detail.orderId()),
            detail.status(),
            detail.cancelReason(),
            detail.totalAmount(),
            detail.orderedAt(),
            detail.paymentDueAt(),
            new ShippingAddress(address.recipientName(), address.phone(), address.zipCode(), address.address()),
            detail.items().stream().map(OrderDetailResponse::toItem).toList());
    }

    private static Item toItem(OrderDetail.Item item) {
        OrderDetail.Child child = item.child();
        return new Item(
            toText(item.orderItemId()),
            toText(item.productId()),
            toText(item.optionId()),
            toText(item.sellerId()),
            item.productName(),
            item.size(),
            item.unitPrice(),
            item.quantity(),
            item.amount(),
            item.status(),
            child == null ? null : new Child(toText(child.childId()), child.name(), child.heightCm(),
                child.weightKg(), child.bodyType()));
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
