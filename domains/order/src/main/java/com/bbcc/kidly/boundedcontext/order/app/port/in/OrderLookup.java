package com.bbcc.kidly.boundedcontext.order.app.port.in;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 주문이 다른 컨텍스트(장바구니 · 배송)에 내주는 주문 조회(#617 · #641). 구현은 주문 app이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface OrderLookup {

    /**
     * 주문(주문상품 포함). 없으면 빈 값.
     */
    Optional<OrderView> findOrder(Long orderId);

    /**
     * 판매 주문 조회용(#997): 주문(주문상품 포함)들을 한 번에 읽되 자녀 정보는 읽지도 내주지도 않는다(판매자에게 자녀 정보를 넘기려면
     * 제3자 제공 동의가 필요). 없는 ID는 빠진다. 자녀가 필요한 쪽(장바구니 정리)은 {@link #findOrder}(자녀 포함)를 쓴다.
     */
    List<SellerOrderView> findOrdersForSeller(Collection<Long> orderIds);

    /**
     * 다른 컨텍스트가 읽는 주문 한 건. 상태 · 구매확정 종류는 enum 이름 문자열.
     *
     * @param orderedAt 주문 시각(주문 생성 시각)
     * @param shippingAddress 배송지. 없으면 null
     */
    record OrderView(
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

    /**
     * 판매 주문 조회가 읽는 주문 한 건(#997). {@link OrderView}에서 주문상품의 자녀 칸을 뺀 모양.
     */
    record SellerOrderView(
        Long orderId,
        Long memberId,
        LocalDateTime orderedAt,
        OrderView.ShippingAddress shippingAddress,
        List<SellerItem> items) {

        public record SellerItem(
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
            String unavailableReason) {
        }
    }
}
