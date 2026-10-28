package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    List<Item> items,
    List<SellerDelivery> deliveries) {

    public record ShippingAddress(String recipientName, String phone, String zipCode, String address) {
    }

    /**
     * @param child 자녀를 고르지 않았으면 null
     * @param unavailableReason 재고 차감 실패로 취소됐을 때 이 주문상품을 살 수 없던 사유. 그 밖에는 null
     * @param sellerName 판매자의 지금 상호명(판매자 복사본, 주문상품에 저장하지 않음). 복사본이 없으면 null
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
        String unavailableReason,
        String sellerName) {

        public static Item from(OrderItem item, String sellerName) {
            OrderItem.ProductSnapshot product = item.getProduct();
            OrderItem.ChildSnapshot child = item.getChild();
            return new Item(
                item.getId(),
                product.productId(),
                product.optionId(),
                product.sellerId(),
                product.productName(),
                product.optionName(),
                product.size(),
                product.unitPrice(),
                item.getQuantity(),
                item.getAmount(),
                item.getStatus().name(),
                item.getDeliveredAt(),
                item.getConfirmedAt(),
                item.getConfirmType() == null ? null : item.getConfirmType().name(),
                child == null ? null
                    : new Child(child.childId(), child.name(), child.heightCm(), child.weightKg(), child.bodyType()),
                item.getUnavailableReason(),
                sellerName);
        }
    }

    public record Child(Long childId, String name, BigDecimal heightCm, BigDecimal weightKg, String bodyType) {
    }

    /**
     * 판매자별 배송. 결제완료 전이면 목록이 비어 있다.
     *
     * @param carrier 발송 전이면 null
     */
    public record SellerDelivery(
        Long deliveryId,
        Long sellerId,
        String status,
        String carrier,
        String trackingNumber,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt) {
    }

    /**
     * @param sellerNames 판매자 ID별 지금 상호명. 없는 판매자는 null로 둔다
     */
    public static OrderDetail from(Order order, List<Delivery> deliveries, Map<Long, String> sellerNames) {
        Order.ShippingAddress address = order.getShippingAddress();
        return new OrderDetail(
            order.getId(),
            order.getStatus().name(),
            order.getCancelReason() == null ? null : order.getCancelReason().name(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            order.getPaymentDueAt(),
            new ShippingAddress(address.recipientName(), address.phone(), address.zipCode(), address.address()),
            order.getItems().stream()
                .map(item -> Item.from(item, sellerNames.get(item.getProduct().sellerId())))
                .toList(),
            deliveries.stream().map(OrderDetail::toDelivery).toList());
    }

    private static SellerDelivery toDelivery(Delivery delivery) {
        return new SellerDelivery(
            delivery.getId(),
            delivery.getSellerId(),
            delivery.getStatus().name(),
            delivery.getCarrier() == null ? null : delivery.getCarrier().name(),
            delivery.getTrackingNumber(),
            delivery.getShippedAt(),
            delivery.getDeliveredAt());
    }
}
