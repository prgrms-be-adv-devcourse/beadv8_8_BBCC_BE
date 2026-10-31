package com.bbcc.kidly.boundedcontext.delivery.in.web.dto;

import com.bbcc.kidly.boundedcontext.delivery.app.dto.SellerOrderView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 판매자 주문 목록의 한 줄(배송 1건). 주문상품은 그 판매자 몫만, 주문 상세의 주문상품과 같은 모양.
 * 배송지 · 주문상품 · 자녀 모양은 주문 상세 응답(OrderDetailResponse)과 같은 칸으로 둔다(#617 컨텍스트별 복사).
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
    ShippingAddress shippingAddress,
    List<Item> items) {

    public record ShippingAddress(String recipientName, String phone, String zipCode, String address) {
    }

    /**
     * @param child 자녀를 고르지 않았으면 null
     * @param unavailableReason 재고 차감 실패로 취소됐을 때 이 주문상품을 살 수 없던 사유. 그 밖에는 null
     * @param sellerName 판매 주문 목록에서는 늘 null(주문 상세의 주문상품과 같은 칸)
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
    }

    public record Child(String childId, String name, BigDecimal heightCm, BigDecimal weightKg, String bodyType) {
    }

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
            address == null ? null : new ShippingAddress(
                address.recipientName(), address.phone(), address.zipCode(), address.address()),
            view.items().stream().map(SellerOrderResponse::toItem).toList());
    }

    /**
     * 판매자 화면이라 상호명(sellerName)은 조회하지 않고 늘 null로 둔다(주문 상세와 같은 항목 모양만 맞춤).
     */
    private static Item toItem(SellerOrderView.Item item) {
        SellerOrderView.Child child = item.child();
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
            child == null ? null : new Child(toText(child.childId()), child.name(),
                child.heightCm(), child.weightKg(), child.bodyType()),
            item.unavailableReason(),
            null);
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
