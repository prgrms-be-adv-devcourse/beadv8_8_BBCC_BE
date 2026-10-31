package com.bbcc.kidly.boundedcontext.delivery.app.dto;

import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.api.OrderView;
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

    public static SellerOrderView of(Delivery delivery, OrderView order) {
        OrderView.ShippingAddress address = order.shippingAddress();
        return new SellerOrderView(
            delivery.getId(),
            delivery.getOrderId(),
            delivery.getStatus().name(),
            delivery.getCarrier() == null ? null : delivery.getCarrier().name(),
            delivery.getTrackingNumber(),
            delivery.getShippedAt(),
            delivery.getDeliveredAt(),
            order.orderedAt(),
            address == null ? null
                : new ShippingAddress(address.recipientName(), address.phone(), address.zipCode(), address.address()),
            order.items().stream()
                .filter(item -> Objects.equals(item.sellerId(), delivery.getSellerId()))
                .map(SellerOrderView::toItem)
                .toList());
    }

    /**
     * 주문을 읽지 못한 배송. 배송 값은 그대로, 주문에 딸린 값(주문 시각 · 배송지)은 null, 주문상품은 빈 목록.
     */
    public static SellerOrderView withoutOrder(Delivery delivery) {
        return new SellerOrderView(
            delivery.getId(),
            delivery.getOrderId(),
            delivery.getStatus().name(),
            delivery.getCarrier() == null ? null : delivery.getCarrier().name(),
            delivery.getTrackingNumber(),
            delivery.getShippedAt(),
            delivery.getDeliveredAt(),
            null,
            null,
            List.of());
    }

    private static Item toItem(OrderView.Item item) {
        OrderView.Child child = item.child();
        return new Item(
            item.orderItemId(),
            item.productId(),
            item.optionId(),
            item.sellerId(),
            item.productName(),
            item.optionName(),
            item.size(),
            item.unitPrice(),
            item.quantity(),
            item.amount(),
            item.status(),
            item.deliveredAt(),
            item.confirmedAt(),
            item.confirmType(),
            child == null ? null
                : new Child(child.childId(), child.name(), child.heightCm(), child.weightKg(), child.bodyType()),
            item.unavailableReason());
    }
}
