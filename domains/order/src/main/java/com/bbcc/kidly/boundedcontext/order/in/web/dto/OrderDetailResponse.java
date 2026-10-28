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
    List<Item> items,
    List<Delivery> deliveries) {

    public record ShippingAddress(String recipientName, String phone, String zipCode, String address) {
    }

    /**
     * @param child 자녀를 고르지 않았으면 null
     * @param unavailableReason 재고 차감 실패로 취소됐을 때 이 주문상품을 살 수 없던 사유. 그 밖에는 null
     * @param sellerName 판매자의 지금 상호명. 판매자 정보가 아직 없으면 null(판매 주문 목록에서는 늘 null)
     */
    public record Item(
        String orderItemId,
        String productId,
        String optionId,
        String sellerId,
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
        String unavailableReason,
        String sellerName) {

        public static Item from(OrderDetail.Item item) {
            OrderDetail.Child child = item.child();
            return new Item(
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
                child == null ? null : new Child(toText(child.childId()), child.name(), child.heightCm(),
                    child.weightKg(), child.bodyType()),
                item.unavailableReason(),
                item.sellerName());
        }
    }

    public record Child(String childId, String name, BigDecimal heightCm, BigDecimal weightKg, String bodyType) {
    }

    /**
     * 판매자별 배송. 결제완료 전이면 비어 있다.
     */
    public record Delivery(
        String sellerOrderId,
        String sellerId,
        String status,
        String carrier,
        String trackingNumber,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt) {
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
            detail.items().stream().map(Item::from).toList(),
            detail.deliveries().stream().map(OrderDetailResponse::toDelivery).toList());
    }

    private static Delivery toDelivery(OrderDetail.SellerDelivery delivery) {
        return new Delivery(
            toText(delivery.deliveryId()),
            toText(delivery.sellerId()),
            delivery.status(),
            delivery.carrier(),
            delivery.trackingNumber(),
            delivery.shippedAt(),
            delivery.deliveredAt());
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
