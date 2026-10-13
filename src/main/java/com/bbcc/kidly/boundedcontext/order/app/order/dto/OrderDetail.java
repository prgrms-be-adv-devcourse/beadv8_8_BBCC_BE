package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세. 상태 · 사유는 enum 이름 문자열.
 *
 * @param cancelReason 취소가 아니면 null
 * @param orderedAt 주문 시각(생성 시각)
 */
public record OrderDetail(
    Long orderId,
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
        Long orderItemId,
        Long productId,
        Long optionId,
        Long sellerId,
        String productName,
        String size,
        long unitPrice,
        int quantity,
        long amount,
        String status,
        Child child) {
    }

    public record Child(Long childId, String name, BigDecimal heightCm, BigDecimal weightKg, String bodyType) {
    }

    public static OrderDetail from(Order order) {
        Order.ShippingAddress address = order.getShippingAddress();
        return new OrderDetail(
            order.getId(),
            order.getStatus().name(),
            order.getCancelReason() == null ? null : order.getCancelReason().name(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            order.getPaymentDueAt(),
            new ShippingAddress(address.recipientName(), address.phone(), address.zipCode(), address.address()),
            order.getItems().stream().map(OrderDetail::toItem).toList());
    }

    private static Item toItem(OrderItem item) {
        OrderItem.ProductSnapshot product = item.getProduct();
        OrderItem.ChildSnapshot child = item.getChild();
        return new Item(
            item.getId(),
            product.productId(),
            product.optionId(),
            product.sellerId(),
            product.productName(),
            product.size(),
            product.unitPrice(),
            item.getQuantity(),
            item.getAmount(),
            item.getStatus().name(),
            child == null ? null
                : new Child(child.childId(), child.name(), child.heightCm(), child.weightKg(), child.bodyType()));
    }
}
