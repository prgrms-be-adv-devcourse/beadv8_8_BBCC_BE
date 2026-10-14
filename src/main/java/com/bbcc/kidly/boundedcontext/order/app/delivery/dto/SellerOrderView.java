package com.bbcc.kidly.boundedcontext.order.app.delivery.dto;

import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 판매자 주문 목록의 한 줄(배송 1건). 주문상품은 그 판매자 몫만.
 *
 * @param orderedAt 주문 시각(주문 생성 시각)
 */
public record SellerOrderView(
    Long deliveryId,
    Long orderId,
    String status,
    String carrier,
    String trackingNumber,
    LocalDateTime shippedAt,
    LocalDateTime deliveredAt,
    LocalDateTime orderedAt,
    ShippingAddress shippingAddress,
    List<Item> items) {

    public record ShippingAddress(String recipientName, String phone, String zipCode, String address) {
    }

    /**
     * 주문 상세의 주문상품과 같은 모양.
     *
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
        LocalDateTime deliveredAt,
        LocalDateTime confirmedAt,
        String confirmType,
        Child child) {
    }

    public record Child(Long childId, String name, BigDecimal heightCm, BigDecimal weightKg, String bodyType) {
    }

    public static SellerOrderView of(Delivery delivery, Order order) {
        Order.ShippingAddress address = order.getShippingAddress();
        return new SellerOrderView(
            delivery.getId(),
            delivery.getOrderId(),
            delivery.getStatus().name(),
            delivery.getCarrier() == null ? null : delivery.getCarrier().name(),
            delivery.getTrackingNumber(),
            delivery.getShippedAt(),
            delivery.getDeliveredAt(),
            order.getCreatedAt(),
            new ShippingAddress(address.recipientName(), address.phone(), address.zipCode(), address.address()),
            order.getItems().stream()
                .filter(item -> Objects.equals(item.getProduct().sellerId(), delivery.getSellerId()))
                .map(SellerOrderView::toItem)
                .toList());
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
            item.getDeliveredAt(),
            item.getConfirmedAt(),
            item.getConfirmType() == null ? null : item.getConfirmType().name(),
            child == null ? null
                : new Child(child.childId(), child.name(), child.heightCm(), child.weightKg(), child.bodyType()));
    }
}
