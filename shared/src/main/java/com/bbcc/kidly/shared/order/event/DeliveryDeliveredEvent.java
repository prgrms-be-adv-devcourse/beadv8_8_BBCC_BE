package com.bbcc.kidly.shared.order.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 배송 한 건이 배송 완료됐다(#900). 판매자 수동 완료 · 택배 조회 자동 완료 중 처음 완료될 때 한 번 나간다(이미 완료면 나가지 않는다).
 * 배송은 주문 × 판매자 단위라 한 주문에서 판매자 수만큼 나올 수 있다. 배송지 · 수령인 · 연락처는 넣지 않는다(개인정보).
 * 대상이 배송 한 건이라 {@code aggregateId}는 배송 ID, 같은 주문의 흐름을 묶도록 {@code correlationId}는 주문 ID다.
 */
public record DeliveryDeliveredEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param deliveryId 배송 ID(판매 주문 한 건)
     * @param orderId 주문 ID
     * @param memberId 주문한 회원(구매자) ID
     * @param sellerId 이 배송의 판매자(회원 ID)
     * @param carrier 택배사 코드(CJ · EPOST · HANJIN · LOTTE · LOGEN, 개발용 DUMMY)
     * @param carrierName 택배사 화면용 이름(예: CJ대한통운)
     * @param trackingNumber 운송장 번호
     * @param deliveredAt 배송 완료 시각(판매자 수동 완료면 그 시각, 택배 조회 완료면 택배사가 알려 준 시각)
     * @param autoConfirmAt 자동 구매확정 예정 시각(#1061, 추가 칸) = 이 배송 주문상품의 배송 완료 시각 + 주문의 자동 구매확정 기간.
     *     주문이 실제로 자동 확정하는 기준과 같다(이 시각부터 확정 대상, 직접 확정 · 다음 주기 실행에 따라 더 늦게 확정될 수 있다).
     *     예전 메시지 · 주문상품이 없으면 null
     * @param items 이 배송(판매자 몫)의 주문상품(#1061, 추가 칸). 취소된 주문상품은 빠진다. 예전 메시지에는 없다(null)
     */
    public record Data(
        Long deliveryId,
        Long orderId,
        Long memberId,
        Long sellerId,
        String carrier,
        String carrierName,
        String trackingNumber,
        LocalDateTime deliveredAt,
        LocalDateTime autoConfirmAt,
        List<Item> items) {

        /**
         * 자동 구매확정 예정 시각 · 주문상품 칸이 없던 예전 모양(#900).
         */
        public Data(Long deliveryId, Long orderId, Long memberId, Long sellerId, String carrier, String carrierName,
            String trackingNumber, LocalDateTime deliveredAt) {
            this(deliveryId, orderId, memberId, sellerId, carrier, carrierName, trackingNumber, deliveredAt, null,
                null);
        }
    }

    /**
     * 배송된 주문상품 한 줄(#1061).
     *
     * @param orderItemId 주문상품 ID
     * @param productId 상품 ID
     * @param quantity 수량
     */
    public record Item(Long orderItemId, Long productId, int quantity) {
    }

    /**
     * 대상이 배송이라 계약 이름을 명시한다.
     */
    @Override
    public String eventType() {
        return "delivery.delivered";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.deliveryId());
    }

    @Override
    public String correlationId() {
        return String.valueOf(data.orderId());
    }
}
