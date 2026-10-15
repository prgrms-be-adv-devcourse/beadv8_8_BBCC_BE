package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import com.bbcc.kidly.boundedcontext.order.app.delivery.dto.SellerOrderView;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 판매자 주문 목록의 한 줄(배송 1건). 주문상품은 그 판매자 몫만, 주문 상세의 주문상품과 같은 모양.
 */
public record SellerOrderResponse(
    String sellerOrderId,
    String orderId,
    String status,
    String carrier,
    String trackingNumber,
    LocalDateTime shippedAt,
    LocalDateTime deliveredAt,
    LocalDateTime orderedAt,
    OrderDetailResponse.ShippingAddress shippingAddress,
    List<OrderDetailResponse.Item> items) {

    public static SellerOrderResponse from(SellerOrderView view) {
        SellerOrderView.ShippingAddress address = view.shippingAddress();
        return new SellerOrderResponse(
            String.valueOf(view.deliveryId()),
            String.valueOf(view.orderId()),
            view.status(),
            view.carrier(),
            view.trackingNumber(),
            view.shippedAt(),
            view.deliveredAt(),
            view.orderedAt(),
            new OrderDetailResponse.ShippingAddress(
                address.recipientName(), address.phone(), address.zipCode(), address.address()),
            view.items().stream().map(SellerOrderResponse::toItem).toList());
    }

    private static OrderDetailResponse.Item toItem(SellerOrderView.Item item) {
        SellerOrderView.Child child = item.child();
        return new OrderDetailResponse.Item(
            toText(item.orderItemId()),
            toText(item.productId()),
            toText(item.optionId()),
            toText(item.sellerId()),
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
            child == null ? null : new OrderDetailResponse.Child(toText(child.childId()), child.name(),
                child.heightCm(), child.weightKg(), child.bodyType()));
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
