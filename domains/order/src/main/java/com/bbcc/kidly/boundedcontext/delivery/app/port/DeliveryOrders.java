package com.bbcc.kidly.boundedcontext.delivery.app.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 배송할 주문 읽기 · 배송 결과를 주문에 남기기(#641). 구현은 out의 주문 컨텍스트 어댑터이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface DeliveryOrders {

    /**
     * 주문(주문상품 포함). 없으면 빈 값.
     */
    Optional<Order> findOrder(Long orderId);

    /**
     * 주문(주문상품 포함)들을 한 번에 읽는다. 없는 ID는 빠진다.
     */
    List<Order> findOrdersWithItems(Collection<Long> orderIds);

    /**
     * 그 판매자 몫 주문상품에 배송 완료 시각을 남긴다. 주문이 없으면 아무것도 하지 않는다.
     */
    void recordDelivered(Long orderId, Long sellerId, LocalDateTime deliveredAt);

    /**
     * 배송 상태가 바뀐 이력 한 줄을 주문 상태 이력에 남긴다.
     *
     * @param from 이전 상태 이름. 새로 만들었으면 null
     */
    void recordHistory(Long orderId, Long deliveryId, Actor actor, String from, String to, String reason,
        LocalDateTime occurredAt);

    /**
     * 판매자 발송 전 몫 취소를 주문에 반영한다(#655): 그 판매자 주문상품 취소(모두면 주문도 취소) · 이력 · order-item.cancelled.
     *
     * @param reason 취소 사유 이름
     * @param memo 판매자 메모. 없으면 null
     * @return 주문이 없거나 취소할 주문상품이 없으면 빈 값(아무것도 바꾸지 않음)
     */
    Optional<CancelledShare> cancelSellerItems(Long orderId, Long sellerId, String reason, String memo, Actor actor,
        LocalDateTime now);

    /**
     * @param orderCancelled 이 취소로 주문이 취소됐으면 true
     */
    record CancelledShare(Long cancellationId, List<Long> orderItemIds, boolean orderCancelled) {
    }

    /**
     * 배송할 주문 한 건. 상태 · 구매확정 종류는 enum 이름 문자열.
     *
     * @param orderedAt 주문 시각(주문 생성 시각)
     * @param shippingAddress 배송지. 없으면 null
     */
    record Order(
        Long orderId,
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
     * 배송 상태를 바꾼 주체. 주문 상태 이력의 주체 종류 이름(BUYER · SELLER · ADMIN · SYSTEM · EVENT) · 회원 ID · 요청 · 작업 · 이벤트 이름.
     *
     * @param id 회원 ID. 시스템 · 이벤트는 null
     */
    record Actor(String type, Long id, String source) {
    }
}
