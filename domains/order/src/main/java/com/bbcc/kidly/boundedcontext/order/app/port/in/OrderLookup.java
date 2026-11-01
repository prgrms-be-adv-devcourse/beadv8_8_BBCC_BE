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
     * 주문(주문상품 포함)들을 한 번에 읽는다. 없는 ID는 빠진다.
     */
    List<OrderView> findOrdersWithItems(Collection<Long> orderIds);

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
}
